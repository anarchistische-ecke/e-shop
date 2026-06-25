#!/usr/bin/env python3
import argparse
import base64
import datetime
import hashlib
import hmac
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

NS = "http://s3.amazonaws.com/doc/2006-03-01/"
ET.register_namespace("", NS)
CORS_RULE_ID = "catalogue-media-upload"
LIFECYCLE_RULE_ID = "catalogue-media-upload-pending-cleanup"


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bucket", required=True)
    parser.add_argument("--endpoint", default=os.getenv("YANDEX_STORAGE_ENDPOINT", "https://storage.yandexcloud.net"))
    parser.add_argument("--region", default=os.getenv("YANDEX_STORAGE_REGION", "ru-central1"))
    parser.add_argument("--pending-prefix", default=os.getenv("CATALOGUE_MEDIA_PENDING_PREFIX", "media-upload-pending"))
    parser.add_argument("--origin", action="append", dest="origins")
    parser.add_argument("--backup-dir")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--apply", action="store_true")
    return parser.parse_args()


class S3ConfigClient:
    def __init__(self, bucket, endpoint, region):
        self.bucket = bucket
        self.endpoint = endpoint.rstrip("/")
        self.region = region
        self.access_key = required_env("YANDEX_STORAGE_KEY")
        self.secret_key = required_env("YANDEX_STORAGE_SECRET")

    def request(self, method, subresource, body=b""):
        now = datetime.datetime.now(datetime.timezone.utc)
        amz_date = now.strftime("%Y%m%dT%H%M%SZ")
        date_stamp = now.strftime("%Y%m%d")
        endpoint = urllib.parse.urlparse(self.endpoint)
        host = endpoint.netloc
        canonical_uri = f"/{urllib.parse.quote(self.bucket, safe='')}/"
        canonical_query = f"{subresource}="
        payload_hash = hashlib.sha256(body).hexdigest()
        headers = {
            "host": host,
            "x-amz-content-sha256": payload_hash,
            "x-amz-date": amz_date,
        }
        if body:
            headers["content-type"] = "application/xml"
            headers["content-md5"] = base64.b64encode(hashlib.md5(body).digest()).decode("ascii")
        signed_header_names = sorted(headers)
        canonical_headers = "".join(f"{name}:{headers[name]}\n" for name in signed_header_names)
        signed_headers = ";".join(signed_header_names)
        canonical_request = "\n".join([
            method,
            canonical_uri,
            canonical_query,
            canonical_headers,
            signed_headers,
            payload_hash,
        ])
        scope = f"{date_stamp}/{self.region}/s3/aws4_request"
        string_to_sign = "\n".join([
            "AWS4-HMAC-SHA256",
            amz_date,
            scope,
            hashlib.sha256(canonical_request.encode()).hexdigest(),
        ])
        signing_key = self.signing_key(date_stamp)
        signature = hmac.new(signing_key, string_to_sign.encode(), hashlib.sha256).hexdigest()
        authorization = (
            "AWS4-HMAC-SHA256 "
            f"Credential={self.access_key}/{scope}, "
            f"SignedHeaders={signed_headers}, "
            f"Signature={signature}"
        )
        request_headers = {name: value for name, value in headers.items() if name != "host"}
        request_headers["Authorization"] = authorization
        url = f"{self.endpoint}{canonical_uri}?{subresource}"
        request = urllib.request.Request(url, data=body if body else None, method=method, headers=request_headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as error:
            return error.code, error.read()

    def signing_key(self, date_stamp):
        key_date = sign(("AWS4" + self.secret_key).encode(), date_stamp)
        key_region = sign(key_date, self.region)
        key_service = sign(key_region, "s3")
        return sign(key_service, "aws4_request")


def sign(key, message):
    return hmac.new(key, message.encode(), hashlib.sha256).digest()


def required_env(name):
    value = os.getenv(name, "").strip()
    if not value:
        raise SystemExit(f"Missing required environment variable: {name}")
    return value


def read_config(client, name):
    status, body = client.request("GET", name)
    if status == 404:
        return None, body
    if status != 200:
        raise RuntimeError(f"Could not read bucket {name} configuration: HTTP {status}: {body.decode(errors='replace')}")
    return ET.fromstring(body), body


def write_config(client, name, root):
    body = ET.tostring(root, encoding="utf-8", xml_declaration=True)
    status, response = client.request("PUT", name, body)
    if status not in (200, 204):
        raise RuntimeError(f"Could not update bucket {name} configuration: HTTP {status}: {response.decode(errors='replace')}")


def merge_cors(root, origins):
    if root is None:
        root = ET.Element(q("CORSConfiguration"))
    remove_rule(root, "CORSRule", CORS_RULE_ID)
    rule = ET.SubElement(root, q("CORSRule"))
    add_text(rule, "ID", CORS_RULE_ID)
    for origin in origins:
        add_text(rule, "AllowedOrigin", origin)
    add_text(rule, "AllowedMethod", "PUT")
    add_text(rule, "AllowedHeader", "*")
    add_text(rule, "ExposeHeader", "ETag")
    add_text(rule, "MaxAgeSeconds", "3600")
    return root


def merge_lifecycle(root, prefix):
    if root is None:
        root = ET.Element(q("LifecycleConfiguration"))
    remove_rule(root, "Rule", LIFECYCLE_RULE_ID)
    rule = ET.SubElement(root, q("Rule"))
    add_text(rule, "ID", LIFECYCLE_RULE_ID)
    add_text(rule, "Status", "Enabled")
    filter_node = ET.SubElement(rule, q("Filter"))
    add_text(filter_node, "Prefix", prefix.rstrip("/") + "/")
    expiration = ET.SubElement(rule, q("Expiration"))
    add_text(expiration, "Days", "7")
    noncurrent = ET.SubElement(rule, q("NoncurrentVersionExpiration"))
    add_text(noncurrent, "NoncurrentDays", "7")
    abort = ET.SubElement(rule, q("AbortIncompleteMultipartUpload"))
    add_text(abort, "DaysAfterInitiation", "1")
    return root


def remove_rule(root, tag, rule_id):
    for rule in list(root.findall(q(tag))):
        identifier = rule.find(q("ID"))
        if identifier is not None and identifier.text == rule_id:
            root.remove(rule)


def has_cors_rule(root, origins):
    rule = find_rule(root, "CORSRule", CORS_RULE_ID)
    if rule is None:
        return False
    actual_origins = [node.text for node in rule.findall(q("AllowedOrigin"))]
    return (
        actual_origins == origins
        and texts(rule, "AllowedMethod") == ["PUT"]
        and texts(rule, "AllowedHeader") == ["*"]
        and texts(rule, "ExposeHeader") == ["ETag"]
        and text(rule, "MaxAgeSeconds") == "3600"
    )


def has_lifecycle_rule(root, prefix):
    rule = find_rule(root, "Rule", LIFECYCLE_RULE_ID)
    if rule is None:
        return False
    return (
        text(rule, "Status") == "Enabled"
        and nested_text(rule, "Filter", "Prefix") == prefix.rstrip("/") + "/"
        and nested_text(rule, "Expiration", "Days") == "7"
        and nested_text(rule, "NoncurrentVersionExpiration", "NoncurrentDays") == "7"
        and nested_text(rule, "AbortIncompleteMultipartUpload", "DaysAfterInitiation") == "1"
    )


def find_rule(root, tag, rule_id):
    if root is None:
        return None
    for rule in root.findall(q(tag)):
        if text(rule, "ID") == rule_id:
            return rule
    return None


def q(tag):
    return f"{{{NS}}}{tag}"


def add_text(parent, tag, value):
    node = ET.SubElement(parent, q(tag))
    node.text = value
    return node


def text(parent, tag):
    node = parent.find(q(tag))
    return node.text if node is not None else None


def texts(parent, tag):
    return [node.text for node in parent.findall(q(tag))]


def nested_text(parent, outer, inner):
    node = parent.find(q(outer))
    return text(node, inner) if node is not None else None


def backup(directory, name, body):
    if not directory:
        return
    target = pathlib.Path(directory)
    target.mkdir(parents=True, exist_ok=True)
    (target / name).write_bytes(body)


def main():
    args = parse_args()
    origins = args.origins or [
        "https://cms.yug-postel.ru",
        "http://localhost:8055",
        "http://127.0.0.1:8055",
    ]
    client = S3ConfigClient(args.bucket, args.endpoint, args.region)
    cors_root, cors_raw = read_config(client, "cors")
    lifecycle_root, lifecycle_raw = read_config(client, "lifecycle")
    backup(args.backup_dir, "cors-before.xml", cors_raw)
    backup(args.backup_dir, "lifecycle-before.xml", lifecycle_raw)

    if args.check:
        valid = has_cors_rule(cors_root, origins) and has_lifecycle_rule(lifecycle_root, args.pending_prefix)
        print("media upload bucket configuration: " + ("ready" if valid else "missing or mismatched"))
        return 0 if valid else 1

    write_config(client, "cors", merge_cors(cors_root, origins))
    write_config(client, "lifecycle", merge_lifecycle(lifecycle_root, args.pending_prefix))
    updated_cors, _ = read_config(client, "cors")
    updated_lifecycle, _ = read_config(client, "lifecycle")
    if not has_cors_rule(updated_cors, origins) or not has_lifecycle_rule(updated_lifecycle, args.pending_prefix):
        raise RuntimeError("Bucket configuration verification failed after update")
    print("media upload bucket configuration applied and verified")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)

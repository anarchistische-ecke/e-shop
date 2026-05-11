export function createStorefrontOpsApi(api) {
  async function bridgeRequest(path, options = {}) {
    const response = await api.request({
      url: `/storefront-ops-bridge${path}`,
      method: options.method || 'GET',
      params: options.params,
      data: options.data,
      headers: options.headers,
    });
    return response.data;
  }

  async function directusRequest(path, options = {}) {
    const response = await api.request({
      url: path,
      method: options.method || 'GET',
      params: options.params,
      data: options.data,
    });
    return response?.data?.data ?? response?.data ?? null;
  }

  return {
    bridgeRequest,
    directusRequest,
  };
}

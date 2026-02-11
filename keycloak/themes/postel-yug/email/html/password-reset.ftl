<!doctype html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Сброс пароля</title>
</head>
<body style="margin:0;padding:0;background:#f4efe9;color:#2b2221;font-family:'Segoe UI',Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="padding:24px 0;">
    <tr>
        <td align="center">
            <table role="presentation" width="640" cellpadding="0" cellspacing="0" style="max-width:640px;width:100%;background:#ffffff;border-radius:22px;overflow:hidden;box-shadow:0 10px 30px rgba(0,0,0,0.08);">
                <tr>
                    <td style="padding:28px 32px 12px;">
                        <div style="font-size:12px;letter-spacing:0.24em;text-transform:uppercase;color:#9b9087;">ПОСТЕЛЬНОЕ БЕЛЬЕ-ЮГ</div>
                        <h1 style="margin:16px 0 10px;font-size:30px;line-height:1.15;font-family:'Times New Roman',Georgia,serif;font-weight:600;color:#2b2221;">Сброс пароля</h1>
                        <p style="margin:0 0 14px;color:#4f4a46;font-size:16px;line-height:1.5;">Мы получили запрос на восстановление доступа в ${realmName!'Постельное Белье-Юг'}.</p>
                        <div style="margin:24px 0 8px;">
                            <a href="${link!'#'}" style="display:inline-block;background:#b65b4a;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:999px;font-weight:600;font-size:15px;">Задать новый пароль</a>
                        </div>
                        <p style="margin:8px 0 0;color:#6a625d;font-size:14px;line-height:1.45;">Ссылка действует ${linkExpiration!60} минут.</p>
                    </td>
                </tr>
                <tr>
                    <td style="padding:0 32px 26px;color:#8b817b;font-size:12px;line-height:1.5;">
                        Если вы не запрашивали сброс пароля, проигнорируйте это письмо.
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>

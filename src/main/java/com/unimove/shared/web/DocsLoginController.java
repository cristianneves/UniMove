package com.unimove.shared.web;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Tela de login da documentacao (Swagger). O POST e tratado pelo formLogin do
 * {@code DocsSecurityConfig}; aqui so se desenha o formulario com o CSRF.
 */
@Hidden
@RestController
public class DocsLoginController {

    @GetMapping(value = "/docs/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String notice = "";
        if (request.getParameter("error") != null) {
            notice = "<p class=\"err\">" + HtmlUtils.htmlEscape(failureMessage(request)) + "</p>";
        } else if (request.getParameter("logout") != null) {
            notice = "<p class=\"ok\">Sessão encerrada.</p>";
        }
        return PAGE
                .replace("{{notice}}", notice)
                .replace("{{csrfName}}", HtmlUtils.htmlEscape(csrf.getParameterName()))
                .replace("{{csrfToken}}", HtmlUtils.htmlEscape(csrf.getToken()));
    }

    private static String failureMessage(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object ex = session == null ? null : session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        // Credencial errada e nao-admin dao a mesma resposta (sem enumeracao);
        // bloqueio, suspensao e senha temporaria trazem o motivo.
        if (ex instanceof AuthenticationException ae && !(ae instanceof org.springframework.security.authentication.BadCredentialsException)) {
            return ae.getMessage();
        }
        return "E-mail ou senha inválidos, ou a conta não é de administrador.";
    }

    private static final String PAGE = """
            <!doctype html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>UniMove API — acesso</title>
              <style>
                body { font-family: system-ui, sans-serif; background: #f4f5f7; margin: 0;
                       display: flex; min-height: 100vh; align-items: center; justify-content: center; }
                form { background: #fff; padding: 32px; border-radius: 8px; width: 100%; max-width: 340px;
                       box-shadow: 0 1px 4px rgba(0,0,0,.12); box-sizing: border-box; margin: 16px; }
                h1 { font-size: 20px; margin: 0 0 4px; }
                small { color: #666; display: block; margin-bottom: 20px; }
                label { display: block; font-size: 14px; margin: 12px 0 4px; }
                input { width: 100%; padding: 10px; border: 1px solid #ccc; border-radius: 6px; box-sizing: border-box; }
                button { margin-top: 20px; width: 100%; padding: 10px; border: 0; border-radius: 6px;
                         background: #1f6feb; color: #fff; font-size: 15px; cursor: pointer; }
                .err { color: #b42318; font-size: 14px; } .ok { color: #067647; font-size: 14px; }
              </style>
            </head>
            <body>
              <form method="post" action="/docs/login">
                <h1>UniMove API</h1>
                <small>Documentação restrita a administradores.</small>
                {{notice}}
                <label for="email">E-mail</label>
                <input id="email" name="email" type="email" autocomplete="username" required autofocus>
                <label for="password">Senha</label>
                <input id="password" name="password" type="password" autocomplete="current-password" required>
                <input type="hidden" name="{{csrfName}}" value="{{csrfToken}}">
                <button type="submit">Entrar</button>
              </form>
            </body>
            </html>
            """;
}

package ru.trafficmarkering.service.email;

public final class EmailTemplate {

    private EmailTemplate() {
    }

    public static String getLoginCodeEmail(String code) {
        return load("templates/email-login-code.html").replace("%CODE%", esc(code));
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String load(String templatePath) {
        try (var stream = EmailTemplate.class.getClassLoader().getResourceAsStream(templatePath)) {
            if (stream == null) {
                throw new IllegalStateException("Шаблон письма не найден: " + templatePath);
            }
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Не получилось использовать шаблон письма: " + templatePath, e);
        }
    }
}

package net.runelite.client.proxy;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionSet;
import net.runelite.client.plugins.microbot.Microbot;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Configures the JVM to use a SOCKS5 proxy if the appropriate command line argument is provided.
 */
public class ProxyConfiguration {

    /**
     * Sets up the proxy configuration based on the provided options.
     * Supports both URL format (--proxy) and separate parameters (--proxy-type, --proxy-host, etc.)
     * 
     * @param options Command line options
     * @param proxyInfo URL-based proxy specification
     * @param proxyType Proxy type (socks5, socks4, http)
     * @param proxyHost Proxy host
     * @param proxyPort Proxy port
     * @param proxyUser Proxy username
     * @param proxyPass Proxy password
     */
    public static void setupProxy(
            OptionSet options,
            ArgumentAcceptingOptionSpec<String> proxyInfo,
            ArgumentAcceptingOptionSpec<String> proxyType,
            ArgumentAcceptingOptionSpec<String> proxyHost,
            ArgumentAcceptingOptionSpec<Integer> proxyPort,
            ArgumentAcceptingOptionSpec<String> proxyUser,
            ArgumentAcceptingOptionSpec<String> proxyPass) {
        
        // Check if separate parameters are provided
        boolean hasSeparateParams = options.has(proxyHost);
        boolean hasUrlFormat = options.has(proxyInfo);
        
        if (!hasSeparateParams && !hasUrlFormat) {
            // No proxy configuration provided
            return;
        }
        
        if (hasSeparateParams && hasUrlFormat) {
            Microbot.showMessage("Cannot use both --proxy and separate proxy parameters (--proxy-host, etc.) at the same time. Please choose one method.");
            System.exit(1);
        }
        
        String host;
        int port;
        String scheme;
        String user = null;
        String pass = null;
        
        if (hasSeparateParams) {
            // Use separate parameters
            host = options.valueOf(proxyHost);
            port = options.has(proxyPort) ? options.valueOf(proxyPort) : 1080;
            scheme = options.has(proxyType) ? options.valueOf(proxyType).toLowerCase(Locale.ROOT) : "socks5";
            user = options.has(proxyUser) ? options.valueOf(proxyUser) : null;
            pass = options.has(proxyPass) ? options.valueOf(proxyPass) : null;
            
            if (host == null || host.isEmpty()) {
                Microbot.showMessage("Proxy host is required when using separate proxy parameters");
                System.exit(1);
            }
        } else {
            // Use URL format (legacy)
            URI uri = URI.create(options.valueOf(proxyInfo));
            
            if (options.has("proxy-type")) {
                Microbot.showMessage("Proxy type is no longer supported, please use the format -proxy=socks://user:pass@host:port or http://user:pass@host:port");
                System.exit(1);
            }
            
            host = uri.getHost();
            scheme = Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
            port = validatePort(uri.getPort());
            
            String[] credentials = extractCredentials(uri);
            user = credentials[0];
            pass = credentials[1];
        }
        
        validateProxyScheme(scheme);
        validatePort(port);
        
        configureProxy(host, port);
        
        if (user != null && !user.isEmpty()) {
            setupAuthenticator(user, pass);
        }
    }

    /**
     * Validates the proxy scheme to ensure it is SOCKS5.
     * @param scheme
     */
    private static void validateProxyScheme(String scheme) {
        boolean isHttpProxy = scheme.equals("http") || scheme.equals("https");
        if (isHttpProxy) {
            Microbot.showMessage("HTTP(S) proxies are not supported, please use a SOCKS5 proxy. \n\n This is to make sure that osrs traffic is also routed through the proxy.");
            System.exit(1);
        }

        boolean isSocksProxy = scheme.equals("socks") || scheme.equals("socks5");
        if (!isSocksProxy) {
            Microbot.showMessage("Proxy scheme must be socks(5).");
            System.exit(1);
        }
    }

    /**
     * Validates the proxy port to ensure it is a positive integer.
     * @param port
     * @return
     */
    private static int validatePort(int port) {
        if (port <= 0) {
            Microbot.showMessage("Invalid proxy port");
            System.exit(1);
        }
        return port;
    }

    /**
     * Extracts the username and password from the URI's user info.
     * @param uri
     * @return
     */
    private static String[] extractCredentials(URI uri) {
        String user = null;
        String pass = null;
        if (uri.getUserInfo() != null && uri.getUserInfo().contains(":")) {
            String[] userInfo = uri.getUserInfo().split(":", 2);
            user = userInfo[0];
            pass = userInfo[1];
        }
        return new String[]{user, pass};
    }

    /**
     * Configures the JVM to use the specified SOCKS5 proxy.
     * @param host
     * @param port
     */
    private static void configureProxy(String host, int port) {
        System.setProperty("socksProxyHost", host);
        System.setProperty("socksProxyPort", String.valueOf(port > 0 ? port : 1080));
    }

    /**
     * Sets up the default authenticator for proxy authentication.
     * @param user
     * @param pass
     */
    private static void setupAuthenticator(String user, String pass) {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass != null ? pass.toCharArray() : new char[0]);
            }
        });
    }
}
package org.b3log.symphony.util;

import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public final class NodeConfiguration {

    private final String publicUrl;
    private final String internalAddress;
    private final String name;
    private final int weight;

    private NodeConfiguration(final String publicUrl, final String internalAddress, final String name,
                              final int weight) {
        this.publicUrl = publicUrl;
        this.internalAddress = internalAddress;
        this.name = name;
        this.weight = weight;
    }

    public static List<NodeConfiguration> parse(final String value) {
        final List<NodeConfiguration> nodes = new ArrayList<>();
        for (final String entry : value.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            final String[] fields = entry.split(",", -1);
            if (fields.length != 3 && fields.length != 4) {
                throw new IllegalArgumentException("Invalid chatroom.node.url entry");
            }
            final boolean legacy = fields.length == 3;
            final String publicUrl = fields[0].trim();
            final String internalAddress = legacy ? "" : fields[1].trim();
            final String name = fields[legacy ? 1 : 2].trim();
            final int weight = Integer.parseInt(fields[legacy ? 2 : 3].trim());
            final URI publicUri = URI.create(publicUrl);
            if (!("ws".equalsIgnoreCase(publicUri.getScheme()) || "wss".equalsIgnoreCase(publicUri.getScheme()))
                    || publicUri.getHost() == null || publicUri.getPort() == 0 || weight < 1
                    || publicUri.getUserInfo() != null || publicUri.getFragment() != null) {
                throw new IllegalArgumentException("Invalid chatroom.node.url entry");
            }
            if (!"wss".equalsIgnoreCase(publicUri.getScheme())
                    && !(legacy && "ws".equalsIgnoreCase(publicUri.getScheme())
                    && isLoopback(publicUri.getHost()))) {
                throw new IllegalArgumentException("Public node URL must use wss");
            }
            if (!internalAddress.isEmpty()) {
                parseInternalAddress(internalAddress);
            }
            nodes.add(new NodeConfiguration(publicUrl, internalAddress, name, weight));
        }
        return nodes;
    }

    static URI parseInternalAddress(final String address) {
        final URI parsed = URI.create("//" + address);
        if (parsed.getHost() == null || parsed.getUserInfo() != null || parsed.getPath() != null
                && !parsed.getPath().isEmpty() || parsed.getQuery() != null || parsed.getFragment() != null) {
            throw new IllegalArgumentException("Invalid internal node address");
        }
        final int port = parsed.getPort() >= 0 ? parsed.getPort() : 10831;
        if (port < 1 || port > 65535 || !isPrivateIp(parsed.getHost())) {
            throw new IllegalArgumentException("Invalid internal node port");
        }
        return URI.create("//" + parsed.getHost() + ":" + port);
    }

    private static boolean isLoopback(final String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static boolean isPrivateIp(final String host) {
        if (host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            final String[] octets = host.split("\\.");
            final int first = Integer.parseInt(octets[0]);
            final int second = Integer.parseInt(octets[1]);
            for (final String octet : octets) {
                if (Integer.parseInt(octet) > 255) {
                    return false;
                }
            }
            return first == 10 || first == 172 && second >= 16 && second <= 31
                    || first == 192 && second == 168;
        }
        if (!host.contains(":")) {
            return false;
        }
        try {
            final byte[] address = InetAddress.getByName(host).getAddress();
            return address.length == 16 && ((address[0] & 0xFE) == 0xFC
                    || (address[0] & 0xFF) == 0xFE && (address[1] & 0xC0) == 0x80);
        } catch (final UnknownHostException e) {
            return false;
        }
    }

    public URI internalWebSocketUri() {
        if (internalAddress.isEmpty()) {
            return null;
        }
        final URI publicUri = URI.create(publicUrl);
        final URI address = parseInternalAddress(internalAddress);
        final String path = publicUri.getRawPath() == null ? "" : publicUri.getRawPath();
        final String query = publicUri.getRawQuery() == null ? "" : "?" + publicUri.getRawQuery();
        return URI.create("ws://" + address.getRawAuthority() + path + query);
    }

    public URI publicWebSocketUri() {
        return URI.create(publicUrl);
    }

    public URI uri(final boolean internal) {
        return internal ? internalWebSocketUri() : publicWebSocketUri();
    }

    public String publicUrl() {
        return publicUrl;
    }

    public String internalAddress() {
        return internalAddress;
    }

    public String name() {
        return name;
    }

    public int weight() {
        return weight;
    }
}

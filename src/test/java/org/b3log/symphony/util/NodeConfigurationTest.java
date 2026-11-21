/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.util;

import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class NodeConfigurationTest {

    @Test
    public void parsesLegacyAndInternalNodeEntries() {
        final List<NodeConfiguration> nodes = NodeConfiguration.parse(
                "wss://rhyus-beijing.fishpi.cn/chat-room-channel,10.0.24.8,北京一区,5;"
                        + "wss://rhyus-hebei.fishpi.cn/chat-room-channel,,河北一区,5;"
                        + "ws://127.0.0.1:10831,本地测试区,1");

        assertEquals(nodes.size(), 3);
        assertEquals(nodes.get(0).internalWebSocketUri(),
                URI.create("ws://10.0.24.8:10831/chat-room-channel"));
        org.testng.Assert.assertNull(nodes.get(1).internalWebSocketUri());
        assertEquals(nodes.get(1).publicWebSocketUri().getScheme(), "wss");
        assertEquals(nodes.get(2).name(), "本地测试区");
        assertEquals(nodes.get(2).weight(), 1);
    }

    @Test
    public void usesExplicitInternalPortAndPreservesPublicQuery() {
        final NodeConfiguration node = NodeConfiguration.parse(
                "wss://node.fishpi.cn:8443/channel?region=cn,10.0.24.8:12000,节点,2").getFirst();

        assertEquals(node.internalWebSocketUri(), URI.create("ws://10.0.24.8:12000/channel?region=cn"));
        assertEquals(node.publicWebSocketUri(), URI.create("wss://node.fishpi.cn:8443/channel?region=cn"));
    }

    @Test
    public void rejectsInvalidNewFormatAndInternalPort() {
        assertThrows(IllegalArgumentException.class,
                () -> NodeConfiguration.parse("ws://node.fishpi.cn,,节点,1"));
        assertThrows(IllegalArgumentException.class,
                () -> NodeConfiguration.parse("wss://node.fishpi.cn,10.0.24.8:70000,节点,1"));
        assertThrows(IllegalArgumentException.class,
                () -> NodeConfiguration.parse("wss://node.fishpi.cn,public.example.com,节点,1"));
        assertThrows(IllegalArgumentException.class,
                () -> NodeConfiguration.parse("wss://user@node.fishpi.cn,,节点,1"));
        assertThrows(IllegalArgumentException.class,
                () -> NodeConfiguration.parse("wss://node.fishpi.cn/path#fragment,,节点,1"));
    }
}

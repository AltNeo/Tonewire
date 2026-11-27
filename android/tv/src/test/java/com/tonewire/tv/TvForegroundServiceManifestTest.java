package com.tonewire.tv;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class TvForegroundServiceManifestTest {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Test
    public void specialUseTypeHasMatchingPermissionAndSubtype() throws Exception {
        File manifest = findManifest();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(manifest);

        assertPermission(document, "android.permission.FOREGROUND_SERVICE");
        assertPermission(document, "android.permission.FOREGROUND_SERVICE_SPECIAL_USE");

        Element service = findService(document, ".TvEqualizerService");
        assertEquals("specialUse", service.getAttributeNS(ANDROID_NS, "foregroundServiceType"));

        NodeList properties = service.getElementsByTagName("property");
        Element subtype = null;
        for (int i = 0; i < properties.getLength(); i++) {
            Element candidate = (Element) properties.item(i);
            if ("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE".equals(
                    candidate.getAttributeNS(ANDROID_NS, "name"))) {
                subtype = candidate;
                break;
            }
        }
        assertNotNull("special-use service subtype property", subtype);
        assertEquals("audio_equalizer_control", subtype.getAttributeNS(ANDROID_NS, "value"));
    }

    private static void assertPermission(Document document, String expectedName) {
        NodeList permissions = document.getElementsByTagName("uses-permission");
        for (int i = 0; i < permissions.getLength(); i++) {
            Element permission = (Element) permissions.item(i);
            if (expectedName.equals(permission.getAttributeNS(ANDROID_NS, "name"))) return;
        }
        throw new AssertionError("Missing permission " + expectedName);
    }

    private static Element findService(Document document, String expectedName) {
        NodeList services = document.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            if (expectedName.equals(service.getAttributeNS(ANDROID_NS, "name"))) return service;
        }
        throw new AssertionError("Missing service " + expectedName);
    }

    private static File findManifest() {
        File[] candidates = {
                new File("src/main/AndroidManifest.xml"),
                new File("android/tv/src/main/AndroidManifest.xml")
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) return candidate;
        }
        throw new AssertionError("Could not locate android/tv/src/main/AndroidManifest.xml");
    }
}

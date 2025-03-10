package org.aion.zero.impl.config;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.io.CharSource;
import java.io.IOException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.Test;

/** Test {@link CfgGui} */
public class CfgGuiTest {
    @Test
    public void testGetterSetter() {
        CfgGui unit = new CfgGui();
        CfgGuiLauncher cfgGuiLauncher = new CfgGuiLauncher();

        unit.setCfgGuiLauncher(cfgGuiLauncher);
        assertThat(unit.getCfgGuiLauncher(), is(cfgGuiLauncher));
    }

    @Test
    public void fromXML() throws IOException, XMLStreamException {
        String testXml = "<gui><launcher><stuff-here-does-not-matter /></launcher></gui>";
        XMLStreamReader xmlStream =
                XMLInputFactory.newInstance()
                        .createXMLStreamReader(CharSource.wrap(testXml).openStream());

        CfgGui unit = new CfgGui();
        CfgGuiLauncher cfgGuiLauncher = mock(CfgGuiLauncher.class);

        unit.setCfgGuiLauncher(cfgGuiLauncher);

        unit.fromXML(xmlStream);
        verify(cfgGuiLauncher).fromXML(xmlStream);
    }

    @Test
    public void toXML() throws Exception {
        CfgGui unit = new CfgGui();
        CfgGuiLauncher cfgGuiLauncher = mock(CfgGuiLauncher.class);
        unit.setCfgGuiLauncher(cfgGuiLauncher);
        when(cfgGuiLauncher.toXML()).thenReturn("<cfg-gui-part/>");

        String result = unit.toXML();
        assertThat(result, is("")); // cfg is hidden for now
        //        assertThat(result, is(
        //                "\r\n\t<gui>\r\n" +
        //                        "\t<cfg-gui-part/>\r\n" +
        //                        "\t</gui>"
        //        ));
    }
}

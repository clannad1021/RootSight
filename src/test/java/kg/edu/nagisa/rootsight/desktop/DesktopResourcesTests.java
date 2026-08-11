package kg.edu.nagisa.rootsight.desktop;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 不启动图形环境也能执行的桌面资源完整性测试。
 */
class DesktopResourcesTests {

    /**
     * 确认两个 FXML 文件存在且是合法 XML，避免运行到窗口加载阶段才发现布局损坏。
     */
    @Test
    void shouldProvideWellFormedDesktopViews() {
        assertThatCode(() -> parseXml("/desktop/fxml/main-view.fxml"))
                .doesNotThrowAnyException();
        assertThatCode(() -> parseXml("/desktop/fxml/settings-view.fxml"))
                .doesNotThrowAnyException();
    }

    /**
     * 确认主题样式和角色主视觉已进入 classpath。
     */
    @Test
    void shouldProvideThemeStylesAndMainArtwork() throws Exception {
        try (InputStream cssStream = requiredResource("/desktop/css/rootsight.css")) {
            String css = new String(cssStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(css).contains(".hero-panel", ".diagnosis-card", ".trace-card");
        }

        try (InputStream imageStream = requiredResource("/desktop/images/blue-archive-cast.png")) {
            BufferedImage image = ImageIO.read(imageStream);
            assertThat(image).isNotNull();
            assertThat(image.getWidth()).isGreaterThan(900);
            assertThat(image.getHeight()).isGreaterThan(1200);
        }
    }

    private void parseXml(String resourcePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream inputStream = requiredResource(resourcePath)) {
            factory.newDocumentBuilder().parse(inputStream);
        }
    }

    private InputStream requiredResource(String resourcePath) {
        InputStream inputStream = getClass().getResourceAsStream(resourcePath);
        assertThat(inputStream)
                .as("classpath resource %s", resourcePath)
                .isNotNull();
        return inputStream;
    }
}

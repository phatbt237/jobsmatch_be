package vn.career.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CrisisDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Em muốn chết",
            "Mình không muốn sống nữa",
            "em nghĩ đến chuyện tự tử",
            "TỰ SÁT có đau không",
            "Em hay tự làm đau bản thân",
            "chán quá, muốn kết thúc cuộc đời",
            "em muon chet",
            "khong muon song nua",
            "toi hay tu lam dau minh"})
    void distressIsRecognised(String message) {
        assertThat(CrisisDetector.isCrisis(message)).as(message).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ngành công nghệ thông tin học những gì?",
            "Em nên học từ từ hay học nhanh?",
            "em tu tu chon nganh",
            "Điểm chuẩn ngành y năm ngoái bao nhiêu",
            "Mình rất căng thẳng vì kỳ thi sắp tới",
            "Làm sao để chết mê chết mệt môn toán",
            ""})
    void ordinaryMessagesAreNotFlagged(String message) {
        assertThat(CrisisDetector.isCrisis(message)).as(message).isFalse();
    }

    @Test
    void nullAndBlankAreSafe() {
        assertThat(CrisisDetector.isCrisis(null)).isFalse();
        assertThat(CrisisDetector.isCrisis("   ")).isFalse();
    }

    @Test
    void theSupportMessageIsCaringAndPointsToTrustedPeopleAndProfessionals() {
        String message = CrisisDetector.SUPPORT_MESSAGE;

        assertThat(message).contains("không phải một mình").contains("thầy cô").contains("chuyên gia tâm lý")
                .contains("115").contains("111");
        assertThat(message).doesNotContain("chọn ngành này");
    }
}

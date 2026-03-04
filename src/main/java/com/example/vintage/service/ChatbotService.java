package com.example.vintage.service;

import com.example.vintage.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Arrays;

@Service
public class ChatbotService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}")
    private String geminiApiUrl;

    private final WebClient webClient;

    public ChatbotService() {
        this.webClient = WebClient.builder().build();
    }

    public ChatResponse processMessage(String userMessage) {
        try {
            // Kiểm tra API key - nếu chưa cấu hình thì dùng chatbot offline
            if (geminiApiKey == null || geminiApiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
                return processOfflineChatbot(userMessage);
            }

            // Tạo context về dược phẩm chức năng
            String contextualMessage = createPharmacyContext(userMessage);

            // Tạo request cho Gemini API
            GeminiRequest geminiRequest = createGeminiRequest(contextualMessage);

            // Gọi API Gemini
            GeminiResponse geminiResponse = callGeminiApi(geminiRequest);

            // Xử lý response
            String response = extractResponseText(geminiResponse);

            return new ChatResponse(response, true);

        } catch (Exception e) {
            // Log lỗi chi tiết
            System.err.println("Lỗi chatbot: " + e.getMessage());

            // Fallback về chatbot offline
            return processOfflineChatbot(userMessage);
        }
    }

    private ChatResponse processOfflineChatbot(String userMessage) {
        String message = userMessage.toLowerCase().trim();

        // Câu hỏi về vitamin
        if (message.contains("vitamin") || message.contains("vi tamin")) {
            return new ChatResponse(
                "💊 **Về Vitamin và Thực phẩm chức năng:**\n\n" +
                "• **Vitamin C**: Tăng cường miễn dịch, chống oxy hóa\n" +
                "• **Vitamin D**: Hỗ trợ xương khớp, tăng cường hấp thu canxi\n" +
                "• **Vitamin B**: Hỗ trợ hệ thần kinh, tăng cường năng lượng\n" +
                "• **Multivitamin**: Bổ sung toàn diện các vi chất cần thiết\n\n" +
                "⚠️ **Lưu ý**: Nên uống sau bữa ăn và tuân theo liều lượng khuyến nghị.\n" +
                "📞 **Tư vấn chi tiết**: Vui lòng gọi hotline 1900-xxxx"
            );
        }

        // Câu hỏi về thuốc đau đầu
        if (message.contains("đau đầu") || message.contains("nhức đầu")) {
            return new ChatResponse(
                "🤕 **Về các loại thuốc giảm đau đầu:**\n\n" +
                "• **Paracetamol**: An toàn, ít tác dụng phụ\n" +
                "• **Ibuprofen**: Chống viêm, giảm đau hiệu quả\n" +
                "• **Aspirin**: Giảm đau, chống viêm\n\n" +
                "⚠️ **Cảnh báo quan trọng**:\n" +
                "- Không dùng quá liều lượng khuyến nghị\n" +
                "- Tránh dùng lâu dài mà không tham khảo bác sĩ\n" +
                "- Đau đầu thường xuyên cần khám bác sĩ\n\n" +
                "🏥 **Khuyến nghị**: Nếu đau đầu kéo dài hoặc tái phát, hãy đến gặp bác sĩ để được thăm khám và tư vấn."
            );
        }

        // Câu hỏi về đau bụng
        if (message.contains("đau bụng") || message.contains("đau dạ dày")) {
            return new ChatResponse(
                "🤢 **Về các sản phẩm hỗ trợ tiêu hóa:**\n\n" +
                "• **Men tiêu hóa**: Hỗ trợ tiêu hóa thức ăn\n" +
                "• **Thuốc kháng acid**: Giảm axit dạ dày\n" +
                "• **Probiotics**: Cân bằng hệ vi sinh đường ruột\n\n" +
                "⚠️ **Lưu ý quan trọng**:\n" +
                "- Đau bụng dữ dội cần đến bệnh viện ngay\n" +
                "- Tránh tự ý dùng thuốc khi chưa rõ nguyên nhân\n" +
                "- Ăn nhẹ, uống nhiều nước\n\n" +
                "🏥 **Khuyến nghị**: Nếu triệu chứng không thuyên giảm sau 24h, hãy đến gặp bác sĩ."
            );
        }

        // Câu hỏi về cảm cúm
        if (message.contains("cảm cúm") || message.contains("cảm lạnh") || message.contains("sốt")) {
            return new ChatResponse(
                "🤧 **Về sản phẩm hỗ trợ cảm cúm:**\n\n" +
                "• **Thuốc hạ sốt**: Paracetamol, Ibuprofen\n" +
                "• **Thuốc ho**: Sirô ho, viên ngậm\n" +
                "• **Vitamin C**: Tăng cường miễn dịch\n" +
                "• **Kẽm (Zinc)**: Hỗ trợ hệ miễn dịch\n\n" +
                "💡 **Gợi ý chăm sóc**:\n" +
                "- Nghỉ ngơi đầy đủ\n" +
                "- Uống nhiều nước ấm\n" +
                "- Ăn nhẹ, dễ tiêu\n" +
                "- Giữ ấm cơ thể\n\n" +
                "⚠️ **Khi nào cần gặp bác sĩ**: Sốt >38.5°C kéo dài, khó thở, ho ra máu."
            );
        }

        // Câu hỏi về mỹ phẩm
        if (message.contains("mỹ phẩm") || message.contains("da") || message.contains("kem")) {
            return new ChatResponse(
                "🧴 **Về các sản phẩm chăm sóc da:**\n\n" +
                "• **Kem dưỡng ẩm**: Giữ da mềm mại, không khô\n" +
                "• **Kem chống nắng**: Bảo vệ da khỏi tia UV\n" +
                "• **Serum Vitamin C**: Chống lão hóa, sáng da\n" +
                "• **Gel trị mụn**: Hỗ trợ giảm viêm, kháng khuẩn\n\n" +
                "💡 **Gợi ý chăm sóc da**:\n" +
                "- Rửa mặt nhẹ nhàng 2 lần/ngày\n" +
                "- Dùng kem chống nắng mỗi ngày\n" +
                "- Uống đủ nước\n" +
                "- Tránh stress\n\n" +
                "🏪 **Đến cửa hàng**: Để được tư vấn sản phẩm phù hợp với loại da của bạn."
            );
        }

        // Câu hỏi chào hỏi
        if (message.contains("xin chào") || message.contains("hello") || message.contains("hi")) {
            return new ChatResponse(
                "👋 **Xin chào! Tôi là trợ lý tư vấn dược phẩm của Vintage Pharmacy.**\n\n" +
                "Tôi có thể giúp bạn tư vấn về:\n" +
                "💊 Thuốc và thực phẩm chức năng\n" +
                "🩺 Các vấn đề sức khỏe cơ bản\n" +
                "🧴 Sản phẩm chăm sóc sức khỏe\n" +
                "📍 Thông tin cửa hàng\n\n" +
                "Hãy đặt câu hỏi để tôi có thể hỗ trợ bạn tốt nhất! 😊"
            );
        }

        // Câu hỏi về cửa hàng
        if (message.contains("cửa hàng") || message.contains("địa chỉ") || message.contains("giờ mở")) {
            return new ChatResponse(
                "🏪 **Thông tin Vintage Pharmacy:**\n\n" +
                "📍 **Địa chỉ**: 123 Đường ABC, Quận XYZ, TP.HCM\n" +
                "📞 **Hotline**: 1900-xxxx\n" +
                "📧 **Email**: info@vintage-pharmacy.com\n" +
                "🕒 **Giờ mở cửa**: 7:00 - 21:00 (Thứ 2 - Chủ Nhật)\n\n" +
                "🚗 **Giao hàng**: Miễn phí trong bán kính 5km\n" +
                "💳 **Thanh toán**: Tiền mặt, thẻ, chuyển khoản\n\n" +
                "Chúng tôi luôn sẵn sàng phục vụ bạn! 🙂"
            );
        }

        // Câu hỏi mặc định
        return new ChatResponse(
            "🤔 **Cảm ơn bạn đã đặt câu hỏi!**\n\n" +
            "Tôi chuyên tư vấn về:\n" +
            "💊 **Dược phẩm**: Thuốc, vitamin, thực phẩm chức năng\n" +
            "🩺 **Sức khỏe**: Cảm cúm, đau đầu, tiêu hóa, da liễu\n" +
            "🏪 **Dịch vụ**: Thông tin cửa hàng, giao hàng\n\n" +
            "**Ví dụ câu hỏi**:\n" +
            "• \"Tôi bị đau đầu, nên uống thuốc gì?\"\n" +
            "• \"Vitamin C có tác dụng gì?\"\n" +
            "• \"Cửa hàng ở đâu?\"\n\n" +
            "📞 **Tư vấn trực tiếp**: 1900-xxxx\n" +
            "⚠️ **Lưu ý**: Thông tin chỉ mang tính tham khảo. Vui lòng tham khảo ý kiến bác sĩ khi cần thiết."
        );
    }

    private String createPharmacyContext(String userMessage) {
        return "Bạn là một chuyên gia tư vấn dược phẩm chức năng tại cửa hàng Vintage Pharmacy. " +
               "Hãy trả lời các câu hỏi về thuốc, thực phẩm chức năng, sức khỏe một cách chuyên nghiệp và thân thiện. " +
               "Luôn khuyên người dùng tham khảo ý kiến bác sĩ khi cần thiết. " +
               "Chỉ trả lời các câu hỏi liên quan đến dược phẩm, sức khỏe, và y tế. " +
               "Nếu câu hỏi không liên quan, hãy lịch sự từ chối và hướng dẫn về chủ đề phù hợp.\n\n" +
               "Câu hỏi của khách hàng: " + userMessage;
    }

    private GeminiRequest createGeminiRequest(String message) {
        GeminiRequest.Part part = new GeminiRequest.Part(message);
        GeminiRequest.Content content = new GeminiRequest.Content(Arrays.asList(part));
        return new GeminiRequest(Arrays.asList(content));
    }

    private GeminiResponse callGeminiApi(GeminiRequest request) {
        try {
            return webClient.post()
                    .uri(geminiApiUrl + "?key=" + geminiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new RuntimeException("Lỗi khi gọi API Gemini: " + e.getMessage(), e);
        }
    }

    private String extractResponseText(GeminiResponse response) {
        if (response != null &&
            response.getCandidates() != null &&
            !response.getCandidates().isEmpty() &&
            response.getCandidates().get(0).getContent() != null &&
            response.getCandidates().get(0).getContent().getParts() != null &&
            !response.getCandidates().get(0).getContent().getParts().isEmpty()) {

            return response.getCandidates().get(0).getContent().getParts().get(0).getText();
        }

        return "Xin lỗi, tôi không thể tạo ra câu trả lời phù hợp. Vui lòng thử lại.";
    }
}

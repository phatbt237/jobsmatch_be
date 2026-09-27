-- Seed: dimensions and survey v1.
-- NOTE: question wording, weights and mini-test items are STARTING VALUES. They should be reviewed by a
-- career-guidance / psychometrics expert before the product is used with real students.
-- Likert scale used by the FE: 1 = Hoàn toàn không đồng ý, 2 = Không đồng ý, 3 = Bình thường,
-- 4 = Đồng ý, 5 = Hoàn toàn đồng ý.

-- ---------------------------------------------------------------- dimensions (19)
INSERT INTO dimensions (code, name, group_code) VALUES
    ('R', 'Kỹ thuật - Thực hành (Realistic)', 'INTEREST'),
    ('I', 'Nghiên cứu - Phân tích (Investigative)', 'INTEREST'),
    ('A', 'Nghệ thuật - Sáng tạo (Artistic)', 'INTEREST'),
    ('S', 'Xã hội - Hỗ trợ (Social)', 'INTEREST'),
    ('E', 'Quản lý - Kinh doanh (Enterprising)', 'INTEREST'),
    ('C', 'Nghiệp vụ - Tổ chức (Conventional)', 'INTEREST'),
    ('LOGIC', 'Tư duy logic', 'APTITUDE'),
    ('VERBAL', 'Ngôn ngữ', 'APTITUDE'),
    ('SPATIAL', 'Tư duy không gian', 'APTITUDE'),
    ('SOCIAL_SKILL', 'Kỹ năng giao tiếp', 'APTITUDE'),
    ('CREATIVE', 'Sáng tạo', 'APTITUDE'),
    ('NUMERIC', 'Tính toán', 'APTITUDE'),
    ('VAL_INCOME', 'Thu nhập cao', 'VALUE'),
    ('VAL_STABILITY', 'Ổn định', 'VALUE'),
    ('VAL_HELPING', 'Giúp ích cho xã hội', 'VALUE'),
    ('VAL_CREATIVITY', 'Được sáng tạo', 'VALUE'),
    ('VAL_AUTONOMY', 'Tự chủ', 'VALUE'),
    ('VAL_PRESTIGE', 'Vị thế xã hội', 'VALUE'),
    ('VAL_TEAMWORK', 'Làm việc nhóm', 'VALUE');

-- ---------------------------------------------------------------- survey v1 + 4 sections
INSERT INTO surveys (id, version, title, status, published_at) VALUES
    ('00000000-0000-4000-8000-000000000001', 1, 'Khảo sát định hướng nghề nghiệp', 'PUBLISHED', now());

INSERT INTO survey_sections (id, survey_id, order_index, code, title, description) VALUES
    ('00000000-0000-4000-8000-0000000000a1', '00000000-0000-4000-8000-000000000001', 1, 'A',
     'Sở thích nghề nghiệp',
     'Hãy cho biết bạn đồng ý đến mức nào với từng câu. Không có đáp án đúng hay sai, hãy chọn theo cảm nhận thật của bạn.'),
    ('00000000-0000-4000-8000-0000000000a2', '00000000-0000-4000-8000-000000000001', 2, 'B',
     'Năng khiếu',
     'Phần này gồm các câu tự đánh giá và một vài câu hỏi nhỏ để bạn thử sức. Đừng lo, kết quả chỉ để tham khảo.'),
    ('00000000-0000-4000-8000-0000000000a3', '00000000-0000-4000-8000-000000000001', 3, 'C',
     'Điều bạn coi trọng trong công việc',
     'Hãy cho biết mức độ quan trọng của từng điều với bạn khi nghĩ về công việc tương lai.'),
    ('00000000-0000-4000-8000-0000000000a4', '00000000-0000-4000-8000-000000000001', 4, 'D',
     'Điều kiện thực tế',
     'Điểm trung bình các môn, tổ hợp xét tuyển dự kiến, khu vực, ngân sách học phí và mức áp lực từ gia đình. Phần này lưu qua endpoint riêng, không có câu hỏi trắc nghiệm.');

-- ---------------------------------------------------------------- section A: 36 RIASEC Likert + 1 attention check
-- Each dimension has 6 statements, exactly one of them reverse scored. "n" is the round in which the statement is
-- shown; statements are interleaved (R, I, A, S, E, C, R, I, ...) to make straight-lining easier to spot.
WITH raw (dim, n, content, reverse_scored) AS (VALUES
    ('R', 1, 'Tôi thích tự tay sửa xe đạp, quạt điện hoặc đồ dùng bị hỏng trong nhà.', false),
    ('R', 2, 'Tôi thích những tiết thực hành, thí nghiệm hơn là ngồi nghe lý thuyết.', false),
    ('R', 3, 'Tôi thấy hào hứng khi lắp ráp mô hình, robot hoặc linh kiện điện tử.', false),
    ('R', 4, 'Tôi ngại phải động tay động chân với máy móc hay dụng cụ.', true),
    ('R', 5, 'Tôi thích làm việc ngoài trời như làm vườn, cắm trại, trồng cây.', false),
    ('R', 6, 'Tôi thích dùng dụng cụ và máy móc để tạo ra một sản phẩm cụ thể.', false),

    ('I', 1, 'Tôi thích tìm hiểu nguyên nhân vì sao một hiện tượng xảy ra.', false),
    ('I', 2, 'Tôi thích giải các bài toán, câu đố khó dù mất nhiều thời gian.', false),
    ('I', 3, 'Tôi hay đọc thêm về khoa học, công nghệ ngoài sách giáo khoa.', false),
    ('I', 4, 'Tôi thích làm thí nghiệm để kiểm chứng một giả thuyết.', false),
    ('I', 5, 'Tôi hay tự hỏi "tại sao" mỗi khi nghe một thông tin mới.', false),
    ('I', 6, 'Tôi thấy chán khi phải phân tích số liệu hoặc tìm hiểu một vấn đề thật sâu.', true),

    ('A', 1, 'Trong thời gian rảnh, tôi thích vẽ, thiết kế hoặc sáng tác nhạc, viết truyện.', false),
    ('A', 2, 'Tôi ít khi quan tâm đến chuyện làm cho sản phẩm của mình đẹp hay độc đáo.', true),
    ('A', 3, 'Tôi thích thể hiện ý tưởng riêng của mình thay vì làm theo khuôn mẫu có sẵn.', false),
    ('A', 4, 'Tôi để ý đến màu sắc và bố cục khi làm bài thuyết trình hoặc trang trí góc học tập.', false),
    ('A', 5, 'Tôi thích quay, chụp, dựng video hoặc chỉnh sửa ảnh.', false),
    ('A', 6, 'Tôi thích tham gia văn nghệ, kịch, múa hát ở trường.', false),

    ('S', 1, 'Tôi thích giảng lại bài cho bạn bè khi họ chưa hiểu.', false),
    ('S', 2, 'Tôi thấy vui khi giúp người khác vượt qua khó khăn.', false),
    ('S', 3, 'Tôi thích tham gia hoạt động tình nguyện hoặc hoạt động Đoàn, Hội.', false),
    ('S', 4, 'Tôi thường là người bạn bè tìm đến để tâm sự.', false),
    ('S', 5, 'Tôi không thích dành thời gian để chăm sóc hay hỗ trợ người khác.', true),
    ('S', 6, 'Tôi thích làm việc nhóm để cùng hoàn thành một mục tiêu chung.', false),

    ('E', 1, 'Tôi thích làm trưởng nhóm hoặc đứng ra tổ chức hoạt động cho lớp.', false),
    ('E', 2, 'Tôi thích thuyết phục người khác đồng ý với ý kiến của mình.', false),
    ('E', 3, 'Tôi không muốn phải lãnh đạo hay quyết định thay cho cả nhóm.', true),
    ('E', 4, 'Tôi hứng thú với việc bán hàng online hoặc kinh doanh nhỏ.', false),
    ('E', 5, 'Tôi sẵn sàng chấp nhận rủi ro để đạt được mục tiêu lớn.', false),
    ('E', 6, 'Tôi thích thi đua và muốn là người dẫn đầu.', false),

    ('C', 1, 'Tôi thường bỏ qua các chi tiết nhỏ và không thích làm việc theo quy trình cố định.', true),
    ('C', 2, 'Tôi thích sắp xếp sổ sách, tài liệu ngăn nắp theo đúng quy tắc.', false),
    ('C', 3, 'Tôi thích lập kế hoạch chi tiết rồi làm theo từng bước.', false),
    ('C', 4, 'Tôi cẩn thận kiểm tra lại số liệu để tránh sai sót.', false),
    ('C', 5, 'Tôi thích những công việc có quy định rõ ràng và ổn định.', false),
    ('C', 6, 'Tôi quản lý tiền tiêu vặt và chi tiêu cá nhân rất rõ ràng.', false)
), ordered AS (
    SELECT dim, content, reverse_scored,
           row_number() OVER (ORDER BY n, array_position(ARRAY['R', 'I', 'A', 'S', 'E', 'C'], dim)) AS rn
    FROM raw
)
INSERT INTO questions (section_id, order_index, type, content, dimension_code, weight, reverse_scored,
                       is_attention_check, required)
SELECT '00000000-0000-4000-8000-0000000000a1',
       CASE WHEN rn >= 19 THEN rn + 1 ELSE rn END,   -- position 19 is reserved for the attention check
       'LIKERT', content, dim, 1, reverse_scored, false, true
FROM ordered;

INSERT INTO questions (section_id, order_index, type, content, dimension_code, weight, reverse_scored,
                       is_attention_check, expected_value, required)
VALUES ('00000000-0000-4000-8000-0000000000a1', 19, 'LIKERT',
        'Với câu này, hãy chọn "Đồng ý" để cho biết bạn đang đọc kỹ từng câu hỏi.',
        NULL, 1, false, true, '4'::jsonb, true);

-- ---------------------------------------------------------------- section B: 12 self-assessment Likert
WITH raw (dim, n, content) AS (VALUES
    ('LOGIC', 1, 'Tôi dễ dàng tìm ra quy luật trong một dãy số hoặc dãy hình.'),
    ('LOGIC', 2, 'Tôi có thể chia một vấn đề phức tạp thành các bước nhỏ để giải quyết.'),
    ('VERBAL', 1, 'Tôi diễn đạt ý kiến bằng lời nói hoặc bài viết rõ ràng, dễ hiểu.'),
    ('VERBAL', 2, 'Tôi học từ vựng và ngoại ngữ nhanh, nhớ lâu.'),
    ('SPATIAL', 1, 'Tôi dễ hình dung một vật thể từ nhiều góc nhìn khác nhau.'),
    ('SPATIAL', 2, 'Tôi đọc bản đồ, sơ đồ hoặc bản vẽ khá dễ dàng.'),
    ('SOCIAL_SKILL', 1, 'Tôi dễ làm quen và bắt chuyện với người lạ.'),
    ('SOCIAL_SKILL', 2, 'Tôi biết cách làm hòa khi bạn bè có mâu thuẫn.'),
    ('CREATIVE', 1, 'Tôi thường nghĩ ra nhiều ý tưởng mới lạ cho cùng một vấn đề.'),
    ('CREATIVE', 2, 'Cách giải quyết vấn đề của tôi thường khác với số đông.'),
    ('NUMERIC', 1, 'Tôi tính nhẩm nhanh và ít khi nhầm.'),
    ('NUMERIC', 2, 'Tôi thấy thoải mái khi làm việc với bảng số liệu và tỷ lệ phần trăm.')
), ordered AS (
    SELECT dim, content,
           row_number() OVER (ORDER BY n, array_position(
               ARRAY['LOGIC', 'VERBAL', 'SPATIAL', 'SOCIAL_SKILL', 'CREATIVE', 'NUMERIC'], dim)) AS rn
    FROM raw
)
INSERT INTO questions (section_id, order_index, type, content, dimension_code, weight, reverse_scored,
                       is_attention_check, required)
SELECT '00000000-0000-4000-8000-0000000000a2', rn, 'LIKERT', content, dim, 1, false, false, true
FROM ordered;

-- ---------------------------------------------------------------- section B: 6 mini-tests (one correct option each)
-- weight 4 makes one correct answer span as much as a full 1..5 Likert swing (a Likert item spans 4).
INSERT INTO questions (id, section_id, order_index, type, content, dimension_code, weight, reverse_scored,
                       is_attention_check, required) VALUES
    ('00000000-0000-4000-8000-00000000b001', '00000000-0000-4000-8000-0000000000a2', 13, 'MINI_TEST',
     'Dãy số: 2, 6, 12, 20, 30, ... Số tiếp theo trong dãy là số nào?', 'LOGIC', 4, false, false, true),
    ('00000000-0000-4000-8000-00000000b002', '00000000-0000-4000-8000-0000000000a2', 14, 'MINI_TEST',
     'Một chiếc áo giá 400.000 đồng được giảm 25%. Sau đó cửa hàng giảm thêm 10% trên giá đã giảm. Giá cuối cùng là bao nhiêu?',
     'NUMERIC', 4, false, false, true),
    ('00000000-0000-4000-8000-00000000b003', '00000000-0000-4000-8000-0000000000a2', 15, 'MINI_TEST',
     'Một khối lập phương lớn 3 x 3 x 3 được sơn toàn bộ mặt ngoài rồi cắt thành 27 khối lập phương nhỏ bằng nhau. Có bao nhiêu khối nhỏ có đúng 2 mặt được sơn?',
     'SPATIAL', 4, false, false, true),
    ('00000000-0000-4000-8000-00000000b004', '00000000-0000-4000-8000-0000000000a2', 16, 'MINI_TEST',
     'Chọn cặp từ có mối quan hệ giống với cặp "Bác sĩ : Bệnh viện".', 'VERBAL', 4, false, false, true),
    ('00000000-0000-4000-8000-00000000b005', '00000000-0000-4000-8000-0000000000a2', 17, 'MINI_TEST',
     'Tất cả học sinh giỏi Toán đều thích môn Tin học. Nam thích môn Tin học. Kết luận nào sau đây là chắc chắn đúng?',
     'LOGIC', 4, false, false, true),
    ('00000000-0000-4000-8000-00000000b006', '00000000-0000-4000-8000-0000000000a2', 18, 'MINI_TEST',
     'Một xe máy đi quãng đường 90 km với vận tốc 45 km/h. Nếu tăng vận tốc lên 60 km/h thì thời gian đi giảm được bao nhiêu?',
     'NUMERIC', 4, false, false, true);

INSERT INTO question_options (question_id, order_index, label, is_correct) VALUES
    ('00000000-0000-4000-8000-00000000b001', 1, '36', false),
    ('00000000-0000-4000-8000-00000000b001', 2, '40', false),
    ('00000000-0000-4000-8000-00000000b001', 3, '42', true),
    ('00000000-0000-4000-8000-00000000b001', 4, '44', false),

    ('00000000-0000-4000-8000-00000000b002', 1, '260.000 đồng', false),
    ('00000000-0000-4000-8000-00000000b002', 2, '270.000 đồng', true),
    ('00000000-0000-4000-8000-00000000b002', 3, '280.000 đồng', false),
    ('00000000-0000-4000-8000-00000000b002', 4, '300.000 đồng', false),

    ('00000000-0000-4000-8000-00000000b003', 1, '6', false),
    ('00000000-0000-4000-8000-00000000b003', 2, '8', false),
    ('00000000-0000-4000-8000-00000000b003', 3, '12', true),
    ('00000000-0000-4000-8000-00000000b003', 4, '24', false),

    ('00000000-0000-4000-8000-00000000b004', 1, 'Ca sĩ : Bài hát', false),
    ('00000000-0000-4000-8000-00000000b004', 2, 'Thợ may : Cây kim', false),
    ('00000000-0000-4000-8000-00000000b004', 3, 'Giáo viên : Trường học', true),
    ('00000000-0000-4000-8000-00000000b004', 4, 'Học sinh : Sách', false),

    ('00000000-0000-4000-8000-00000000b005', 1, 'Nam giỏi Toán.', false),
    ('00000000-0000-4000-8000-00000000b005', 2, 'Nam không giỏi Toán.', false),
    ('00000000-0000-4000-8000-00000000b005', 3, 'Chưa đủ thông tin để biết Nam có giỏi Toán hay không.', true),
    ('00000000-0000-4000-8000-00000000b005', 4, 'Nam không thích môn Toán.', false),

    ('00000000-0000-4000-8000-00000000b006', 1, '15 phút', false),
    ('00000000-0000-4000-8000-00000000b006', 2, '20 phút', false),
    ('00000000-0000-4000-8000-00000000b006', 3, '30 phút', true),
    ('00000000-0000-4000-8000-00000000b006', 4, '45 phút', false);

-- ---------------------------------------------------------------- section C: 14 value statements + 1 attention check
WITH raw (dim, n, content) AS (VALUES
    ('VAL_INCOME', 1, 'Có mức thu nhập cao là điều quan trọng nhất với tôi khi chọn nghề.'),
    ('VAL_INCOME', 2, 'Tôi sẵn sàng làm việc vất vả hơn để có mức lương cao hơn.'),
    ('VAL_STABILITY', 1, 'Tôi muốn có một công việc ổn định, ít lo bị mất việc.'),
    ('VAL_STABILITY', 2, 'Tôi thích môi trường làm việc quen thuộc và có lộ trình thăng tiến rõ ràng.'),
    ('VAL_HELPING', 1, 'Tôi muốn công việc của mình giúp ích trực tiếp cho người khác hoặc cộng đồng.'),
    ('VAL_HELPING', 2, 'Tôi thấy công việc có ý nghĩa khi nó góp phần làm cho xã hội tốt đẹp hơn.'),
    ('VAL_CREATIVITY', 1, 'Tôi muốn công việc cho phép mình sáng tạo và thử những điều mới.'),
    ('VAL_CREATIVITY', 2, 'Tôi thấy chán với công việc lặp đi lặp lại mỗi ngày.'),
    ('VAL_AUTONOMY', 1, 'Tôi muốn tự quyết định cách làm và cách sắp xếp thời gian của mình.'),
    ('VAL_AUTONOMY', 2, 'Tôi thích làm việc độc lập, không bị giám sát quá chặt.'),
    ('VAL_PRESTIGE', 1, 'Tôi muốn nghề nghiệp của mình được xã hội nể trọng.'),
    ('VAL_PRESTIGE', 2, 'Có chức danh và vị thế cao trong xã hội là điều quan trọng với tôi.'),
    ('VAL_TEAMWORK', 1, 'Tôi thích làm việc trong một tập thể gắn kết.'),
    ('VAL_TEAMWORK', 2, 'Tôi làm việc hiệu quả hơn khi có đồng đội bên cạnh.')
), ordered AS (
    SELECT dim, content,
           row_number() OVER (ORDER BY n, array_position(ARRAY['VAL_INCOME', 'VAL_STABILITY', 'VAL_HELPING',
               'VAL_CREATIVITY', 'VAL_AUTONOMY', 'VAL_PRESTIGE', 'VAL_TEAMWORK'], dim)) AS rn
    FROM raw
)
INSERT INTO questions (section_id, order_index, type, content, dimension_code, weight, reverse_scored,
                       is_attention_check, required)
SELECT '00000000-0000-4000-8000-0000000000a3',
       CASE WHEN rn >= 8 THEN rn + 1 ELSE rn END,   -- position 8 is reserved for the attention check
       'LIKERT', content, dim, 1, false, false, true
FROM ordered;

INSERT INTO questions (section_id, order_index, type, content, dimension_code, weight, reverse_scored,
                       is_attention_check, expected_value, required)
VALUES ('00000000-0000-4000-8000-0000000000a3', 8, 'LIKERT',
        'Để kiểm tra sự tập trung, hãy chọn "Hoàn toàn không đồng ý" ở câu này.',
        NULL, 1, false, true, '1'::jsonb, true);

-- Seed: 5 SAMPLE mentors with 1 published SAMPLE post each (IT, accounting, medicine, graphic design, marketing).
--
-- !! These people and their stories are INVENTED for development and demos. They are flagged is_sample = true and
-- !! the API repeats that flag. Their accounts are LOCKED with an unusable password, nobody can log in as them.
-- !! Replace them with real, verified mentors before going live.

INSERT INTO users (id, email, password_hash, full_name, role, status) VALUES
    ('00000000-0000-4000-8000-00000000c001', 'sample-mentor-1@sample.invalid', '!', 'Người dùng mẫu 1', 'MENTOR', 'LOCKED'),
    ('00000000-0000-4000-8000-00000000c002', 'sample-mentor-2@sample.invalid', '!', 'Người dùng mẫu 2', 'MENTOR', 'LOCKED'),
    ('00000000-0000-4000-8000-00000000c003', 'sample-mentor-3@sample.invalid', '!', 'Người dùng mẫu 3', 'MENTOR', 'LOCKED'),
    ('00000000-0000-4000-8000-00000000c004', 'sample-mentor-4@sample.invalid', '!', 'Người dùng mẫu 4', 'MENTOR', 'LOCKED'),
    ('00000000-0000-4000-8000-00000000c005', 'sample-mentor-5@sample.invalid', '!', 'Người dùng mẫu 5', 'MENTOR', 'LOCKED');

INSERT INTO mentors (user_id, company, job_title, years_experience, major_id, bio, verified, verified_at, is_sample) VALUES
    ('00000000-0000-4000-8000-00000000c001', 'Công ty mẫu A', 'Kỹ sư phần mềm', 6,
     (SELECT id FROM majors WHERE code = 'IT'), 'Hồ sơ mẫu, nhân vật hư cấu.', true, now(), true),
    ('00000000-0000-4000-8000-00000000c002', 'Công ty mẫu B', 'Kế toán tổng hợp', 8,
     (SELECT id FROM majors WHERE code = 'ACCOUNTING'), 'Hồ sơ mẫu, nhân vật hư cấu.', true, now(), true),
    ('00000000-0000-4000-8000-00000000c003', 'Bệnh viện mẫu C', 'Bác sĩ nội khoa', 10,
     (SELECT id FROM majors WHERE code = 'MEDICINE'), 'Hồ sơ mẫu, nhân vật hư cấu.', true, now(), true),
    ('00000000-0000-4000-8000-00000000c004', 'Studio mẫu D', 'Nhà thiết kế đồ họa', 5,
     (SELECT id FROM majors WHERE code = 'GRAPHICDESIGN'), 'Hồ sơ mẫu, nhân vật hư cấu.', true, now(), true),
    ('00000000-0000-4000-8000-00000000c005', 'Công ty mẫu E', 'Chuyên viên marketing', 7,
     (SELECT id FROM majors WHERE code = 'MARKETING'), 'Hồ sơ mẫu, nhân vật hư cấu.', true, now(), true);

INSERT INTO pov_posts (mentor_id, major_id, title, sections, status, published_at, is_sample) VALUES
    ('00000000-0000-4000-8000-00000000c001', (SELECT id FROM majors WHERE code = 'IT'),
     'Một ngày làm kỹ sư phần mềm (bài mẫu)',
     jsonb_build_object(
        'a_day_at_work', 'Buổi sáng mình họp nhóm ngắn để chia việc, sau đó viết và sửa mã nguồn cho tính năng được giao. Buổi chiều thường dành cho đọc mã của đồng nghiệp, viết kiểm thử và xử lý lỗi người dùng báo về.',
        'wish_i_knew', 'Mình ước biết sớm rằng đọc hiểu mã của người khác và giao tiếp rõ ràng quan trọng không kém việc viết mã giỏi. Học cách đặt câu hỏi ngắn gọn giúp tiết kiệm rất nhiều thời gian.',
        'dark_side', 'Công nghệ thay đổi liên tục nên phải học suốt. Đến hạn giao sản phẩm thì áp lực khá lớn và đôi khi phải trực xử lý sự cố ngoài giờ.',
        'who_fits', 'Người thích giải quyết vấn đề từng bước, kiên nhẫn khi gỡ lỗi và không ngại tự học. Không cần giỏi toán xuất sắc nhưng cần tư duy logic.',
        'school_vs_work', 'Trường dạy nền tảng như cấu trúc dữ liệu, mạng, cơ sở dữ liệu. Đi làm thì học thêm quy trình nhóm, công cụ quản lý mã nguồn và cách làm việc với yêu cầu thay đổi liên tục.'),
     'PUBLISHED', now(), true),
    ('00000000-0000-4000-8000-00000000c002', (SELECT id FROM majors WHERE code = 'ACCOUNTING'),
     'Làm kế toán tổng hợp thực tế ra sao (bài mẫu)',
     jsonb_build_object(
        'a_day_at_work', 'Mình đối chiếu chứng từ, hạch toán nghiệp vụ, theo dõi công nợ và chuẩn bị số liệu cho báo cáo. Cuối tháng và cuối quý khối lượng việc tăng mạnh vì phải khóa sổ đúng hạn.',
        'wish_i_knew', 'Mình ước biết sớm rằng phần mềm kế toán và Excel dùng rất nhiều. Sự cẩn thận và ghi chép có hệ thống giúp tránh sai sót về sau.',
        'dark_side', 'Mùa quyết toán thuế khá căng thẳng, công việc đòi hỏi độ chính xác cao nên áp lực tinh thần không nhỏ và ít khi được sáng tạo.',
        'who_fits', 'Người ngăn nắp, trung thực, thích làm việc với con số và tuân thủ quy định. Ai thích thay đổi liên tục có thể thấy hơi đơn điệu.',
        'school_vs_work', 'Trường dạy nguyên lý và chuẩn mực kế toán. Đi làm thì học thêm cách áp dụng vào từng doanh nghiệp, làm việc với cơ quan thuế và phối hợp các phòng ban.'),
     'PUBLISHED', now(), true),
    ('00000000-0000-4000-8000-00000000c003', (SELECT id FROM majors WHERE code = 'MEDICINE'),
     'Một ngày của bác sĩ nội khoa (bài mẫu)',
     jsonb_build_object(
        'a_day_at_work', 'Mình đi buồng thăm khám bệnh nhân nội trú, đọc kết quả xét nghiệm, điều chỉnh phác đồ và trao đổi với điều dưỡng, gia đình người bệnh. Buổi chiều có thể khám ngoại trú hoặc hội chẩn ca khó.',
        'wish_i_knew', 'Mình ước biết sớm rằng kỹ năng lắng nghe và giải thích cho người bệnh cũng quan trọng như kiến thức chuyên môn. Việc chăm sóc bản thân cũng cần được chú ý.',
        'dark_side', 'Thời gian học dài, trực đêm nhiều, áp lực trách nhiệm lớn và đôi khi phải đối diện với những ca không qua khỏi. Cần sức khỏe và sự vững vàng về tinh thần.',
        'who_fits', 'Người có tinh thần giúp đỡ, chịu được áp lực, kiên trì học tập lâu dài và sẵn sàng làm việc trong môi trường nhiều biến động.',
        'school_vs_work', 'Trường dạy kiến thức nền và thực hành lâm sàng. Đi làm thì mỗi ca bệnh là một bài học mới, cần học liên tục và làm việc nhóm chặt chẽ.'),
     'PUBLISHED', now(), true),
    ('00000000-0000-4000-8000-00000000c004', (SELECT id FROM majors WHERE code = 'GRAPHICDESIGN'),
     'Nghề thiết kế đồ họa nhìn từ bên trong (bài mẫu)',
     jsonb_build_object(
        'a_day_at_work', 'Mình nhận yêu cầu từ khách hàng, tìm ý tưởng, phác thảo rồi dựng thiết kế trên máy. Có nhiều vòng chỉnh sửa theo góp ý, và mình cũng dành thời gian tham khảo xu hướng mới.',
        'wish_i_knew', 'Mình ước biết sớm rằng thẩm mỹ là chưa đủ, cần biết lắng nghe khách hàng và bảo vệ ý tưởng bằng lý lẽ. Xây dựng portfolio sớm rất có ích.',
        'dark_side', 'Khách hàng hay thay đổi yêu cầu, thời hạn gấp và đôi khi ý tưởng bị từ chối dù mình rất tâm huyết. Thu nhập giữa các dự án có thể không đều.',
        'who_fits', 'Người thích sáng tạo, để ý cái đẹp và chi tiết, chịu được góp ý và sẵn sàng học công cụ mới liên tục.',
        'school_vs_work', 'Trường dạy nền tảng về bố cục, màu sắc, phần mềm. Đi làm thì học cách làm việc với khách hàng, quản lý thời gian và hoàn thiện sản phẩm theo yêu cầu thực tế.'),
     'PUBLISHED', now(), true),
    ('00000000-0000-4000-8000-00000000c005', (SELECT id FROM majors WHERE code = 'MARKETING'),
     'Làm marketing thực tế không chỉ là quảng cáo (bài mẫu)',
     jsonb_build_object(
        'a_day_at_work', 'Mình theo dõi số liệu chiến dịch, họp với nhóm nội dung và thiết kế, cập nhật kế hoạch truyền thông và làm báo cáo hiệu quả. Có ngày dành nhiều thời gian trao đổi với đối tác và khách hàng.',
        'wish_i_knew', 'Mình ước biết sớm rằng marketing cần đọc số liệu nhiều hơn tưởng tượng. Kỹ năng viết, phân tích và kể chuyện đều hữu ích.',
        'dark_side', 'Kết quả bị đo bằng con số nên áp lực doanh số khá rõ. Xu hướng thay đổi nhanh nên phải cập nhật liên tục và đôi khi làm việc vào cuối tuần khi chạy chiến dịch.',
        'who_fits', 'Người tò mò về hành vi con người, thích sáng tạo nhưng cũng chịu khó làm việc với dữ liệu, giao tiếp tốt và làm việc nhóm được.',
        'school_vs_work', 'Trường dạy khung kiến thức về thị trường, thương hiệu, hành vi tiêu dùng. Đi làm thì học công cụ quảng cáo số, quản lý ngân sách và phối hợp nhiều bộ phận.'),
     'PUBLISHED', now(), true);

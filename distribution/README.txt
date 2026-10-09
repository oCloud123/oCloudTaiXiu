oCloudTaiXiu 1.0.0

Hướng dẫn đầy đủ về lệnh, config và xử lý lỗi: DOCS.md.

Chép oCloudTaiXiu-1.0.0.jar vào plugins/ và restart server.
Cần Vault + economy provider hoặc Essentials economy.
Paper/Folia và các fork giữ API Paper, mục tiêu 1.21 đến 26.3.
Nhà cái không giới hạn quỹ; người chơi vẫn cược/thắng/thua bằng tiền economy.

Plugin tự tạo taixiu.yml, gui.yml và bedrock/taixiu.yml.
Thư mục config/ trong gói này chỉ là bản cấu hình mẫu.
Native Dialog dùng khi server/client hỗ trợ; bản cũ có inventory GUI.
Geyser/Floodgate sử dụng form Bedrock khi API khả dụng.

/tx: mở menu
/tx cuoc <tai|xiu> <tiền>: cược; nhận 100k, 1m, 10%, all-in, max
/tx ban: bàn 3D; /tx cau: cầu; /tx stats: thống kê
/tx status|pause|resume|cancel|reload: quản trị (ocloudtaixiu.admin)
/tx setnext <tai|xiu|boba|random>: phiên mới mở kế tiếp (ocloudtaixiu.setnext)
/tx next: xem kết quả phiên kế đang đặt

Trên Folia, economy provider cũng cần hỗ trợ Folia.
Mạng proxy: backend muốn gửi form cần có Floodgate và nhận dữ liệu/key theo tài liệu Floodgate.

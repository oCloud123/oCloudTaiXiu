# oCloudTaiXiu — Hướng dẫn sử dụng

Tài liệu dành cho quản trị viên cài bản phát hành **1.0.0**. Plugin Tài Xỉu hoạt động độc lập, dùng tiền economy của người chơi và không có tài khoản vốn, ngân khố hay cơ chế lấy tiền từ hoạt động khác của server.

## 1. Cài đặt

1. Chuẩn bị Paper, Folia hoặc fork giữ API tương thích với Paper. Dải phiên bản mục tiêu là **1.21–26.3**. Dùng phiên bản Java mà server yêu cầu; bytecode plugin là Java 21.
2. Cài **Vault + một economy provider**, hoặc Essentials có API economy. Vault riêng lẻ không cung cấp ví tiền. Trên Folia, economy provider cũng phải hỗ trợ Folia.
3. Chép `oCloudTaiXiu-1.0.0.jar` vào thư mục `plugins/`, rồi khởi động lại server.
4. Dùng `/tx status` kiểm tra plugin và `/tx` mở giao diện. Plugin tự tạo các file dưới đây:

```text
plugins/oCloudTaiXiu/
├── taixiu.yml
├── gui.yml
├── bedrock/
│   └── taixiu.yml
└── data/
    └── taixiu.db
```

Các file trong `config/` của gói ZIP là config mẫu. Khi tùy chỉnh, sửa file đã tạo trong `plugins/oCloudTaiXiu/`.

Đã kiểm tra chữ ký API trên 14 phiên bản trong dải mục tiêu, gồm API 26.3 beta. Đây không phải cam kết đã chạy thực tế mọi fork, bản build, client hay mạng proxy. Nên thử trên server thử nghiệm trước khi đưa vào sử dụng chính thức.

## 2. Luật và tiền thưởng

- Mặc định Tài là tổng 11–17, Xỉu là tổng 4–10, sau khi loại bộ ba.
- Mọi bộ ba, như `1-1-1` hoặc `6-6-6`, làm cả cửa Tài và Xỉu thua khi `rules.triples-house-wins: true`.
- Trọng số kết quả mặc định là Tài/Xỉu/bộ ba = **40/40/20**. Đây là trọng số ngẫu nhiên dài hạn, không phải lịch kết quả bảo đảm cho từng nhóm phiên.
- Khi tắt luật bộ ba, plugin dùng ba xúc xắc ngẫu nhiên tự nhiên; phân loại Tài/Xỉu theo tổng, kể cả bộ ba.
- Có thể cộng tiền vào cửa đã chọn trong cùng phiên; không được đổi cửa hay rút cược đã xác nhận.
- Thắng nhận `2 × tiền cược − thuế tính trên tiền cược`. Mặc định cược `100.000` thắng nhận `190.000`, gồm tiền cược ban đầu; lợi nhuận là `90.000`.
- Nhà cái không giới hạn quỹ. Người chơi vẫn phải có đủ tiền trong ví. `bet.maximum: 0` bỏ giới hạn cấu hình, nhưng plugin vẫn kiểm tra phạm vi số để tránh tràn khi cộng cược và tính thưởng.

## 3. Lệnh và quyền

`/taixiu` và `/tx` dùng chung chức năng. Người chơi mặc định có quyền `ocloudtaixiu.use`; quyền quản trị và đặt kết quả mặc định dành cho OP.

| Lệnh | Công dụng | Quyền |
| --- | --- | --- |
| `/tx` | Mở menu; console xem trạng thái | `ocloudtaixiu.use` |
| `/tx cuoc <tai\|xiu> <tiền>` | Đặt cược trực tiếp | `ocloudtaixiu.use` |
| `/tx ban` | Mở bàn cược 3D | `ocloudtaixiu.use` |
| `/tx cau` | Xem cầu lịch sử | `ocloudtaixiu.use` |
| `/tx stats` | Xem thống kê cá nhân | `ocloudtaixiu.use` |
| `/tx status` | Xem trạng thái và tổng cược | `ocloudtaixiu.admin` |
| `/tx pause` | Tạm dừng, hủy phiên đang chạy và xếp hoàn cược | `ocloudtaixiu.admin` |
| `/tx resume` | Tiếp tục hoạt động | `ocloudtaixiu.admin` |
| `/tx cancel` | Hủy phiên hiện tại và xếp hoàn cược | `ocloudtaixiu.admin` |
| `/tx reload` | Tải lại ba file cấu hình | `ocloudtaixiu.admin` |
| `/tx setnext <tai\|xiu\|boba\|random>` | Đặt hoặc xóa kết quả phiên kế | `ocloudtaixiu.setnext` |
| `/tx next` | Xem kết quả đang đặt cho phiên kế | `ocloudtaixiu.setnext` |

Các lệnh quản trị còn chịu quyền lệnh gốc `ocloudtaixiu.use`. Nếu dùng plugin quản lý quyền, cấp quyền gốc cùng quyền chức năng cho nhóm quản trị.

Ví dụ đặt cược:

```text
/tx cuoc tai 100k
/tx cuoc xiu 1.5m
/tx cuoc tai 10%
/tx cuoc tai all-in
/tx cuoc tai max
```

`k` = nghìn, `m` = triệu. Mức phần trăm tính theo số dư hiện tại; `all-in` và `max` chịu giới hạn ví, cấu hình và phạm vi số an toàn. Sau cược đầu tiên, tiếp tục dùng đúng cửa đã chọn.

### Đặt kết quả phiên kế

```text
/tx setnext tai
/tx setnext xiu
/tx setnext boba
/tx setnext random
```

Giá trị áp dụng **một lần cho phiên nhận cược mới mở tiếp theo**, không sửa phiên đang nhận cược hoặc đang lắc. Phiên kế không có người cược vẫn tiêu thụ giá trị đã đặt. Trạng thái chờ được lưu qua restart và reload. `random`/`clear` xóa lựa chọn; `triple`/`bo-ba` là bí danh của `boba`.

Xúc xắc khớp kết quả được đặt. Nếu tắt luật bộ ba, ép `boba` vẫn tạo ba mặt giống nhau nhưng thắng/thua phân loại theo tổng; muốn cả hai cửa thua thì giữ `rules.triples-house-wins: true`.

## 4. Cấu hình game — `taixiu.yml`

| Khóa | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `enabled` | `true` | Bật game |
| `round.betting-seconds` | `90` | Thời gian nhận cược, tối thiểu 10 giây |
| `round.cooldown-seconds` | `10` | Nghỉ giữa phiên, tối thiểu 1 giây |
| `round.mute-invite-after-empty-rounds` | `10` | Tắt sound mời sau chuỗi phiên không có cược; có cược sẽ reset chuỗi |
| `bet.minimum` | `100000` | Tiền cược tối thiểu |
| `bet.maximum` | `0` | Giới hạn tiền cược; 0 bỏ giới hạn cấu hình |
| `bet.tax-percent` | `10` | Thuế trên tiền cược của người thắng, từ 0 đến 100 |
| `rules.triples-house-wins` | `true` | Bộ ba làm cả hai cửa thua |
| `rules.outcome-weights.tai` | `40` | Trọng số kết quả Tài |
| `rules.outcome-weights.xiu` | `40` | Trọng số kết quả Xỉu |
| `rules.outcome-weights.triple` | `20` | Trọng số bộ ba |
| `gui.mode` | `auto` | `auto`, `native`, `inventory` hoặc `3d` |
| `gui.bedrock-forms` | `true` | Dùng form cho người chơi Bedrock khi API khả dụng |
| `announcements.enabled` | `true` | Bật lời mời mở phiên |

Trọng số mỗi cửa từ 0 đến 10000 và tổng phải lớn hơn 0. Các trọng số dùng khi bật luật bộ ba. Muốn trở về xúc xắc ngẫu nhiên tự nhiên, tắt luật bộ ba.

`display.*` chỉnh chữ, icon, Title, ActionBar và bảng 3D; `sounds.*` chỉnh âm thanh. `history-display.*` chỉnh số điểm, thời gian hiển thị, khoảng cách và animation cầu; `betting-display.*` chỉnh khoảng cách và các nút tiền thông minh của bàn cược. Các ghi chú trong file mẫu mô tả từng nhóm.

Chỉnh chữ ở `messages.*` có thể dùng `&a`, `&l` và `&#RRGGBB`. Giữ placeholder có sẵn trong từng dòng, như `%round%`, `%time%`, `%amount%`, `%payout%`, `%tax%`, `%side%`. Không phải mọi placeholder đều được thay ở mọi dòng.

## 5. Native GUI và inventory — `gui.yml`

`gui.mode: auto` hoặc `native` dùng Native Dialog khi API server và client hỗ trợ; nếu không, dùng inventory. `inventory` chọn menu rương. `3d` chọn bàn cược. Người chơi Bedrock được ưu tiên form khi `gui.bedrock-forms: true`.

| Nhóm | Chức năng |
| --- | --- |
| `title` | Tiêu đề menu |
| `native.columns` | Số cột nút |
| `native.button-width`, `native.body-width`, `native.input-width` | Kích thước nút, nội dung và ô nhập |
| `native.input-max-length`, `native.amount-label` | Giới hạn và nhãn ô nhập tiền |
| `buttons.*` | Nhãn nút chọn cửa, xác nhận, quay lại, lịch sử và thống kê |
| `main.*` | Nội dung menu chính |
| `confirm.*` | Tiêu đề, tiền cược và lời nhắc xác nhận |
| `stats.*` | Chữ trong trang thống kê |
| `inventory.amounts` | Các mức tiền, phân cách bằng dấu phẩy |

Ví dụ `inventory.amounts: "100k,500k,1m,10%,25%,50%,all-in,max"`. `main.round` nhận `%round%`, `%state%`; `confirm.amount` nhận `%side%`, `%amount%`.

Nếu dùng ViaVersion và client không hỗ trợ Dialog, hoặc không xác định được khả năng client, plugin chuyển sang inventory.

## 6. Menu Bedrock — `bedrock/taixiu.yml`

File này chỉnh các nhãn riêng của form Bedrock:

| Khóa | Vị trí |
| --- | --- |
| `title` | Tiêu đề menu chính |
| `button-tai` | Nút cược Tài |
| `button-xiu` | Nút cược Xỉu |
| `button-table` | Nút bàn cược 3D |
| `button-history` | Nút xem cầu |
| `button-stats` | Nút thống kê |
| `button-refresh` | Nút cập nhật phiên |
| `amount-label` | Nhãn ô nhập tiền cược |
| `history-title` | Tiêu đề lịch sử |
| `button-back` | Nút quay lại |

Form Bedrock nhận trực tiếp chữ cấu hình; dùng mã màu `§` như file mẫu. Ví dụ:

```yaml
title: "§6§lTÀI XỈU"
button-tai: "§c§lTÀI §r§8• §7Đặt cược"
button-xiu: "§b§lXỈU §r§8• §7Đặt cược"
amount-label: "Nhập tiền — 100k, 1m, 10%, all-in hoặc max"
button-back: "QUAY LẠI"
```

Trang xác nhận còn dùng nhãn trong `gui.yml`. Không phải tất cả chữ form đều thuộc file Bedrock này. Lịch sử qua nút form là dạng chữ; nút bàn cược mở bàn 3D.

### Geyser/Floodgate

Plugin nhận diện người chơi qua API Floodgate/Geyser và UUID Floodgate khi có thể. Gửi form cần API hoạt động trên backend chạy oCloudTaiXiu. Khi gửi menu chính thất bại, plugin chuyển sang inventory và ghi nguyên nhân vào console.

Nếu Geyser/Floodgate chạy trên proxy, cài Floodgate ở backend cần dùng API, bật `send-floodgate-data` trên proxy và dùng đúng `key.pem` cùng cấu hình chuyển tiếp người chơi theo [hướng dẫn Floodgate chính thức](https://geysermc.org/wiki/floodgate/setup/). Giữ `key.pem` riêng tư, không gửi trong gói plugin. Chỉ có Geyser trên proxy không cung cấp API form cục bộ cho backend.

## 7. Reload, dữ liệu và sao lưu

Sau khi sửa config, dùng `/tx reload`. YAML lỗi, sai kiểu hoặc giá trị không hợp lệ sẽ bị từ chối và giữ cấu hình đang chạy. Dùng dấu cách để thụt dòng, lưu UTF-8 và giữ số/boolean đúng kiểu: `true` thay vì `"true"`.

Thuế, luật bộ ba và trọng số được chốt lúc mở phiên; reload không thay chúng giữa phiên. Giới hạn cược và phần trình bày có thể cập nhật khi reload. Nếu tắt game bằng `enabled: false`, phiên đang chạy được hủy và xếp hoàn cược.

SQLite lưu lịch sử, cược, thống kê và thanh toán chờ tại `data/taixiu.db`. Khi economy lỗi, khoản hoàn/thưởng còn chờ sẽ được thử lại định kỳ 30 giây, khi người chơi vào và khi plugin khởi động. Khi tắt plugin, hoàn cược phiên đang chạy được lưu để xử lý lần bật sau.

Vault không có transaction chung với SQLite. Server chết đúng giữa lúc economy trả tiền và lúc lưu trạng thái có thể cần đối soát thủ công. Nếu log báo `PAYMENT_UNCERTAIN` hoặc hoàn tiền thất bại, sao lưu và đối chiếu DB với economy trước khi trả bù.

Dừng server sạch rồi sao lưu thư mục `plugins/oCloudTaiXiu/`. Nếu sao lưu khi SQLite còn mở, dùng phương pháp backup SQLite phù hợp; sao chép riêng file DB có thể thiếu dữ liệu WAL. Muốn chuyển lịch sử từ core cũ, xử lý xong cược còn chờ trên core, dừng server và sao lưu trước khi chuyển `data/taixiu.db`. Không để hai plugin cùng xử lý một DB.

## 8. Xử lý lỗi thường gặp

| Hiện tượng | Kiểm tra |
| --- | --- |
| Không mở phiên | `/tx status`; game có bật/tạm dừng không; economy provider có đăng ký và hoạt động không |
| Không dùng được lệnh | Quyền `ocloudtaixiu.use`, quyền chức năng; tính năng Tài Xỉu của core có gây trùng lệnh không |
| Cược bị từ chối | Phiên còn nhận cược, đủ số dư, đủ mức tối thiểu, đúng cửa đã chọn, không vượt mức tối đa |
| Native GUI thành inventory | Server/client có Dialog API không; ViaVersion có nhận đúng phiên bản client không |
| Bedrock không có form | `gui.bedrock-forms`, API trên backend, cấu hình Floodgate/proxy; xem nguyên nhân trong console |
| Icon xúc xắc lỗi | Font/resource pack client; đổi `display.dice-icons` thành `[1]` đến `[6]` |
| Reload thất bại | Tên file/khóa console báo, cú pháp YAML, kiểu dữ liệu và giá trị |
| Có thưởng/hoàn đang chờ | Khôi phục economy; kiểm tra log trước khi trả bù thủ công |

Khi báo lỗi, gửi phiên bản server/fork, Java, economy provider, Geyser/Floodgate nếu có, lệnh thao tác và đoạn console liên quan. Bỏ mật khẩu, token, khóa Floodgate và thông tin kết nối riêng tư.

## 9. Gói chia sẻ

Gói ZIP gồm `oCloudTaiXiu-1.0.0.jar`, hướng dẫn và config mẫu. Mã và tên class/method được giữ nguyên trong bản phát hành. Không gửi kèm dữ liệu người chơi, mật khẩu, token hay khóa Floodgate.

## 10. Banner console và hỗ trợ

Khi bật và tắt plugin, console hiện logo oCloud, tên/phiên bản, trạng thái và thời gian xử lý. Mỗi banner được ghi thành một khối log nhiều dòng, kèm Discord hỗ trợ: https://discord.gg/WVNrYzQfhX. Console hỗ trợ ANSI sẽ hiển thị logo cyan và trạng thái xanh; lỗi bật/tắt hiển thị đỏ cùng exception để đối soát. Banner không có kiểm tra license.

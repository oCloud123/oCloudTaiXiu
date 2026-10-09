# oCloudTaiXiu

Plugin Tài Xỉu độc lập cho server Minecraft, được quản lý và cập nhật bởi oCloud Studio. Hỗ trợ Paper, Folia và các fork giữ API Paper tương thích như Purpur và Leaf.

## Cài đặt

1. Build bằng `./gradlew.bat build` trên Windows hoặc `./gradlew build` trên Linux, với JDK 25. Bytecode plugin phát hành vẫn nhắm Java 21; server phải dùng Java mà phiên bản server yêu cầu.
2. Chép `build/libs/oCloudTaiXiu-1.0.0.jar` vào `plugins/`.
3. Cài Vault và một plugin economy, hoặc Essentials có API economy. Nhà cái không cần tài khoản hay vốn; người chơi vẫn dùng tiền economy thật.
4. Restart server. Plugin tạo `plugins/oCloudTaiXiu/taixiu.yml`, `gui.yml`, `bedrock/taixiu.yml` và `data/taixiu.db`.

## Luật được giữ lại

- Tài: tổng từ 11 trở lên; Xỉu: tổng từ 10 trở xuống. Mặc định mọi bộ ba làm cả hai cửa thua.
- Giữ trọng số mặc định Tài/Xỉu/bộ ba là 40/40/20 khi `rules.triples-house-wins: true`. Tắt luật bộ ba thì dùng ba xúc xắc ngẫu nhiên tự nhiên như source gốc.
- Thắng nhận `2 × tổng tiền cược − thuế`; thuế mặc định 10% tiền cược. Cho cộng tiền cùng cửa, không cho đổi cửa hoặc rút cược trong một phiên.
- Giữ bàn cược 3D, cầu lịch sử 3D riêng tư, animation xúc xắc, BossBar, ActionBar, âm thanh và thống kê SQLite.
- Bỏ toàn bộ vốn nhà cái, sổ ngân khố, giới hạn rủi ro quỹ, lấy doanh thu shop/lịch sử mua hàng và lệnh thêm/rút quỹ.
- `bet.maximum: 0` cho phép cược theo ví người chơi. Phạm vi số nguyên vẫn được kiểm tra để phép nhân trả thưởng và tổng phiên không tràn số; không phải giới hạn ngân khố.

## Gói chia sẻ

Chạy `./gradlew.bat sharePlugin` để tạo `build/release/oCloudTaiXiu-1.0.0-share.zip`, gồm JAR thường, hướng dẫn và ba config mẫu. JAR cài trực tiếp là `build/release/oCloudTaiXiu-1.0.0.jar`; cùng bản JAR cũng nằm trong `build/libs`.

Build kiểm tra unit test và JAR phát hành với Bukkit giả lập + SQLite thật: khởi động, đặt cược, trả thưởng, hủy/hoàn tiền, ép bộ ba và banner start/stop. Manifest khai báo `paperweight-mappings-namespace: mojang` phù hợp với Paper API.

## Lệnh và quyền

| Lệnh | Chức năng | Quyền |
| --- | --- | --- |
| `/tx`, `/taixiu` | Mở GUI | `ocloudtaixiu.use` (mặc định mọi người) |
| `/tx cuoc <tai\|xiu> <tiền>` | Cược với `100k`, `1.5m`, `10%`, `all-in`, `max` | `ocloudtaixiu.use` |
| `/tx ban` | Bàn cược 3D | `ocloudtaixiu.use` |
| `/tx cau` | Cầu lịch sử 3D | `ocloudtaixiu.use` |
| `/tx stats` | Thống kê cá nhân | `ocloudtaixiu.use` |
| `/tx status` | Trạng thái phiên | `ocloudtaixiu.admin` (mặc định OP) |
| `/tx pause`, `/tx resume` | Dừng/tiếp tục; dừng hủy và xếp hoàn cược | `ocloudtaixiu.admin` |
| `/tx cancel` | Hủy phiên hiện tại và xếp hoàn cược | `ocloudtaixiu.admin` |
| `/tx reload` | Tải lại cả ba tệp cấu hình | `ocloudtaixiu.admin` |
| `/tx setnext <tai\|xiu\|boba\|random>` | Đặt hoặc xóa kết quả phiên kế | `ocloudtaixiu.setnext` (mặc định OP) |
| `/tx next` | Xem kết quả đang đặt cho phiên kế | `ocloudtaixiu.setnext` |

`setnext` áp dụng một lần cho **phiên nhận cược mới mở tiếp theo**, kể cả phiên đó không có ai cược và bị bỏ qua. Phiên đang OPEN/ROLLING không đổi kết quả. Giá trị đang chờ lưu vào SQLite, tồn tại qua restart và reload. `random`/`clear` hủy đặt kết quả; `triple`/`bo-ba` là bí danh của `boba`. Xúc xắc luôn khớp với kết quả đã đặt. Khi tắt luật bộ ba, bộ ba được phân loại theo tổng như luật gốc.

## Cấu hình và GUI

`taixiu.yml` quản lý luật, thời gian, tiền cược, thuế, trọng số, display 3D, âm thanh và thông báo. `gui.mode` nhận `auto`, `native`, `inventory` hoặc `3d`.

`gui.yml` cấu hình tiêu đề, nhãn nút, nội dung xác nhận, nhãn thống kê, số cột, chiều rộng nút/nội dung/input và độ dài input của Native GUI. Có placeholder `%round%`, `%state%` ở `main.round`, `%side%`, `%amount%` ở `confirm.amount`. Tiêu đề và nút hỗ trợ `&a` và `&#RRGGBB`. `inventory.amounts` quản lý các mức tiền ở GUI dự phòng.

Native Dialog được tải riêng khi server có API. API Dialog của Paper bắt đầu từ 1.21.7; các server cũ dùng inventory GUI. Nếu ViaVersion báo client quá cũ hoặc không xác định được client, plugin dùng inventory. Xem [tài liệu Dialog API](https://docs.papermc.io/paper/dev/dialogs/).

Reload lỗi YAML, sai kiểu dữ liệu hoặc giá trị tiền/thuế/kích thước không hợp lệ sẽ bị từ chối và giữ cấu hình đang chạy. Các khóa thiếu lấy mặc định trong bộ nhớ; plugin không ghi đè nội dung tùy chỉnh. Thuế, luật bộ ba và trọng số được chốt khi phiên mở, nên reload không thay đổi chúng giữa phiên. Mức cược tối đa và phần trình bày có thể cập nhật khi reload.

## Paper, Folia và các fork

Mục tiêu là Paper/Folia và các fork giữ API tương thích với Paper, trong dải 1.21–26.3. Plugin dùng Java 21 bytecode, không dùng NMS/CraftBukkit hoặc packet có ID phụ thuộc phiên bản. Server 26.x vẫn phải chạy với phiên bản Java mà server yêu cầu.

Vòng game dùng GlobalRegionScheduler; GUI, âm thanh và tiền của người chơi online dùng EntityScheduler; đọc block nền của bảng Bedrock dùng RegionScheduler. Không chuyển thao tác entity sang global scheduler khi việc lập lịch thất bại. Provider economy cài trên Folia cũng cần hỗ trợ Folia. Xem [hướng dẫn Paper/Folia](https://docs.papermc.io/paper/dev/folia-support/).

Để chạy lại kiểm tra tương thích bytecode với các API trong dải phiên bản:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-compatibility.ps1
```

Script biên dịch một lần với API 1.21, rồi đối chiếu chữ ký method/field của cùng bytecode với từng API server. Báo cáo nằm ở `build/verification/compatibility.csv`; log từng bản ở cùng thư mục. Việc này bổ sung cho unit test và SQLite integration test; chưa thay thế chạy thật từng fork, client Java, Bedrock hoặc một mạng proxy.

## Geyser/Floodgate

- Tự nhận diện qua Floodgate API, Geyser API và UUID Floodgate khi API không truy cập được.
- Form được tạo từ đúng classloader của plugin gửi form, tránh lỗi khi Geyser và Floodgate mang hai bản Cumulus khác nhau.
- Đọc input CustomForm theo API có chỉ số và API cũ; SimpleForm hỗ trợ response mới/cũ và chỉ xử lý một lần để tránh gửi trùng cược.
- Callback trở về EntityScheduler của người chơi trước khi xử lý GUI/cược.
- Có menu cược, xác nhận, lịch sử dạng chữ và thống kê cho Bedrock. Chữ/nút riêng nằm ở `bedrock/taixiu.yml`; xác nhận dùng các khóa trong `gui.yml`.
- Khi không gửi được form, menu chính chuyển sang inventory và ghi nguyên nhân vào log.
- Khi Floodgate chạy trên proxy, cài Floodgate ở backend muốn dùng API, bật `send-floodgate-data` trên proxy và dùng cùng `key.pem` theo [hướng dẫn chính thức](https://geysermc.org/wiki/floodgate/setup/). Geyser chạy chỉ ở proxy không cung cấp API form cục bộ cho backend.

## Lưu cược và thanh toán

Kết quả và nghĩa vụ trả thưởng của cả phiên được lưu cùng một transaction SQLite trước khi gọi economy. Hoàn tiền/trả thưởng thất bại giữ trạng thái pending; plugin thử lại mỗi 30 giây, khi người chơi vào server và khi khởi động. Khi tắt plugin, khoản hoàn của phiên đang chạy được lưu để xử lý ở lần bật sau.

Xác nhận từ GUI cũ không được chuyển sang cược cho phiên khác. Reload config lỗi giữ cấu hình đang chạy. Các cược đến từ nhiều vùng được tuần tự hóa khi ghi sổ chung.

Vault không cung cấp transaction ID hay transaction chung với SQLite. Nếu server chết đúng sau khi economy trả tiền nhưng trước khi SQLite lưu trạng thái, cần đối soát dữ liệu với economy; không thể cam kết exactly-once trong cửa sổ crash đó. Nếu plugin phát hiện trả tiền thành công nhưng ghi trạng thái lỗi, nó cách ly `PAYMENT_UNCERTAIN` khi còn ghi được DB và báo lỗi để quản trị viên đối soát. Giao dịch không lưu được và hoàn bù cũng thất bại được báo rõ cho người chơi và log.

## Hỗ trợ và cập nhật

[oCloud Studio trên Discord](https://discord.gg/WVNrYzQfhX) là nơi hỗ trợ, báo lỗi và theo dõi phiên bản mới.

## Giấy phép

All Rights Reserved. Plugin được cung cấp miễn phí để tải và sử dụng trên server. Xem [LICENSE](LICENSE).

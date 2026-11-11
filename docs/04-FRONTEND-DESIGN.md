---
name: frontend-design
description: Guidance for distinctive, intentional visual design when building new UI or reshaping an existing one. Helps with aesthetic direction, typography, and making choices that don't read as templated defaults.
license: Complete terms in LICENSE.txt
---

# Frontend Design

Approach this as the design lead at a design studio known for giving every client a distinct visual identity that is not mistaken for anyone else's. This client has already rejected proposals that felt cliché or templated, and is paying for a distinctive point of view: make deliberate, opinionated choices about palette, typography, and layout that are specific to this brief, and take aesthetic risk if justified.

## Ground your designs in the subject matter

If the brief does not identify what the product or subject matter is, identify it yourself before designing, and confirm with the client. You can come up with one concrete subject, the design's audience, and the design's primary job, as a proposal. If there's any information in your memory about the client's preferences or context about what they're building, use that as a hint. The subject's industry, subject matter, materials, and vernacular are where distinctive visual choices come from — a design for a toy for girls aged 8–11 will be very aesthetically different from a dashboard for financial analysts. Build with the brief's real content and subject matter throughout.

## Design principles

For web designs, the hero is the first thing viewers will see. Open with the most characteristic thing in the subject's world, in the form that is most appropriate: a headline, an image, an animation, a live demo, an interactive moment, or other treatments. Be deliberate with your choice: a big number with a small label, supporting stats, and a gradient accent is the default treatment, so only use it if that's truly the best option.

Typography carries the personality of the page. You don't need a different typeface for display or headline text and body content: use one family or two, and if two, make them clearly distinct.

Choose your typefaces deliberately, not the default families you would reach for on any other project, and set a clear type scale following the default guidance of The Elements of Typographic Style with intentional weights, widths, and spacing. When type is used as a headline or visual element, use the type treatment itself as an active part of the design, not a neutral delivery vehicle for the content.

Default to line lengths of less than 80 characters. Serif typefaces can have slightly longer line lengths; give serif body text slightly more line-height than a sans-serif.

Avoid these default typographic treatments; they are the commonest tells of a generated page:
- Accenting just a single word or phrase in a headline, like putting one word in italic/bold or a different color.
- Using all caps for labels.
- Adding unnecessary typographic labels above content.

Visual structure is information. Structural devices like outlines, borders, numbering, eyebrows, dividers, labels, etc., encode useful information about the content rather than decorate it. Many generic designs use numbered markers (01 / 02 / 03), but that's only appropriate if the content actually is a sequence — like a stepped process or a timeline. Before adding numbered markers, check the content really is a sequence.

Use non-user-triggered motion sparingly and deliberately, only to draw attention. A single orchestrated moment — one page-load sequence or one reveal — lands better than scattered effects; fade-and-slide-up entrances on each section and hover transitions on every card are the generic default and read as AI-generated. Motion that answers a person's action (opening, expanding, confirming) is welcome when it shows what changed.

Consider written content carefully. Often a design brief may not contain real content, and it's up to you to come up with copy and placeholder content. Copy can make a design feel as templated as the design itself. See the below section on writing for more guidance.

## Process: plan, review against the brief, build, critique

For calibration, AI-generated design right now clusters around some traits:
1. a warm cream background (near #F4F1EA) with a high-contrast serif display and a terracotta or warm-clay accent (often near #D97757 — Anthropic's own Claude-interaction accent, so on a user's brief it reads as a tell);
2. a near-black background with a single bright acid-green or vermilion accent;
3. a broadsheet-style layout with hairline rules, zero border-radius, and dense newspaper-like columns;
4. the SaaS-card kit: content chopped into identical rounded cards, one border-radius on everything regardless of hierarchy, the same soft grey shadow (rgba(0,0,0,.1)) under each, and gradient washes as decoration;
5. template chrome that appears whatever the subject: a tracked-out ALL-CAPS eyebrow label above every heading; meta strings joined with middle dots ('A · B · C'); labels built as 'WORD — fragment' with a spaced em dash; tinted near-black (#0B0B0B, #111) standing in for black; a monospace face for small data labels; a '→' appended to link and button text.

All traits are legitimate for some briefs, but they are defaults rather than choices, and they appear regardless of subject. Where the brief pins down a visual direction, follow it exactly — the brief's own words always win, including when it asks for one of these looks. Where it leaves an axis free, don't spend that freedom on one of these defaults. As with a hired human designer, there's often a careful balance between doing what you're good at and taking each project as a chance to experiment and learn.

Work in two passes. First, brainstorm a short design plan based on the client's design brief: create a compact token system with color, type, layout, and principles.
- Color: describe the core base palette as 4–6 named hex values.
- Type: the typefaces and their roles.
- Layout: a layout concept, using one-sentence prose descriptions and ASCII wireframes to ideate and compare. Include alignment guidance; should the content be left aligned, center aligned, justified?
- Principles: the high-level guidance for what makes this page unique.

Then review that plan against the brief before building: if any part of it reads like the generic default you would produce for any similar page (work through a similar prompt to see if you arrive somewhere similar) rather than a choice made for this specific brief — revise that part, say what you changed and why. Only after you've confirmed the relative uniqueness of your design plan should you start to write the code, following the revised plan.

When writing the code, be careful of structuring your CSS selector specificities. It's easy to generate CSS classes that cancel each other out (especially with a type-based selector like .section and an element-based selector like .cta). This can happen often with padding/margin between sections.

## Restraint and self-critique

Spend your boldness in one place. Let one element be the memorable thing, keep everything around it quiet and disciplined, and cut any decoration that does not serve the brief. Build to a quality floor without announcing it: responsive down to mobile, visible keyboard focus, reduced motion respected, visually accessible, harmonious color palettes. Critique your own work as you build, taking screenshots to review if your environment supports it — a picture is worth 1000 tokens. Consider Chanel's advice: before leaving the house, take a look in the mirror and remove one accessory. Human creatives have memory and always try to do something new, so if you have a space to quickly jot down notes about what you've tried, it can help you in future passes.

## More on writing in design

Words appear in a design for one reason: to make it easier to understand and use. They are design content, not decoration. Bring the same intentionality and minimalism to copywriting that you would bring to spacing and color. Before writing anything, ask what the design needs to say, and how it can best be said to help the person navigate the experience.

Write from the end user's perspective. Name things by what users will understand in simple language, not by how the system is built. A user manages notifications, not webhook config. Describe what something is or does in plain terms rather than selling it. Being specific and legible to new users is always better than being clever.

Use active voice as default. A CTA says exactly what happens when it is used: "Save changes," not "Submit." An action keeps the same name through the whole flow, so the button that says "Publish" produces a toast that says "Published." The vocabulary of an interface is the signposting for someone navigating the product. Cohesion and consistency are how people learn their way around.

Treat failure and emptiness as moments for direction, not mood. Explain what went wrong and how to fix it, in the interface's voice rather than a person's. Errors don't apologize, and they are never vague about what happened. An empty screen is an invitation to act.

Keep the tone conversational: plain verbs, sentence case, no filler, with tone matched to the brand and the audience. Let each written element do exactly one job.

## DenHub — Tìm kiếm Server (2026-10-31)

- Giữ hệ màu tối, font và token có sẵn. Theo điều chỉnh đã duyệt, Home giữ nguyên grid/danh sách Server sở hữu/tham gia, bỏ ô tìm kiếm và nút Khám phá dạng button; chỉ giữ link chữ “Khám phá server” cạnh các thao tác danh sách.
- Dock có kính lúp với tooltip/accessible name “Tìm server”, dẫn tới `/discover`; link chữ ở Home cùng đích và dùng được bằng bàn phím. Trang khám phá dùng vùng nội dung chính, header nhỏ và ô tìm có label rõ ràng, không dùng modal kết quả hoặc hero lớn.
- Kết quả chỉ gồm Server công khai chưa tham gia và không thuộc người dùng: icon 48px, tên có ellipsis, nhãn “Server công khai”, nút “Tham gia”. Không có hàng “Đã tham gia”/“Mở server”. Không hiển thị mô tả, số người online hoặc tag khi chưa có contract/dữ liệu. Khi hết cộng đồng phù hợp hiển thị “Chưa có server mới để khám phá”.
- Có skeleton khi tải, số kết quả khi thành công, thông báo không khớp và nút xóa; tách lỗi mạng cùng nút thử lại khỏi kết quả rỗng. Phân trang 20 hàng; gõ từ khóa mới quay về trang đầu.
- Form tạo/cài đặt có lựa chọn `PRIVATE`/`PUBLIC`, giải thích: riêng tư cần mã mời; công khai cho phép tìm và tham gia trực tiếp. Mặc định riêng tư. Cài đặt chỉ đổi sau lưu; hủy khôi phục cả chế độ.
- Trên màn <=991px, khám phá ẩn sidebar phụ; <=600px dùng dock 56px. Mỗi kết quả mobile xếp nút bên dưới thông tin, không tràn ngang. Giữ nhãn input, focus bàn phím, điều hướng về Home và nút nhập mã mời.
- Nội dung UI dùng “Nhập mã mời / ID”; mô tả giải thích ID chỉ áp dụng cho Server công khai. Không đưa thuật ngữ backend, lỗi SQL hoặc raw exception vào luồng người dùng.


## DenHub — Công cụ tin nhắn nhóm (2026-11-11)

- Giữ token tối và bố cục chat hiện có. Thêm thanh gọn phía trên header: Tìm tin, Tin ghim, Tin đã lưu, Bật/Tắt thông báo; wrap ở màn nhỏ, không thêm trang/hero hoặc dependency.
- Drawer 420px tối đa theo viewport cho search/pins/saved, có label input, loading, kết quả rỗng, lỗi cùng retry, phân trang Tải thêm. Search giữ từ khóa khi reload realtime, đổi Channel thì reset panel/query và bỏ response cũ.
- Menu hover/focus của mỗi tin có Trả lời và Lưu; chỉ Owner thấy Ghim; sửa/xóa giữ giới hạn tác giả. Panel saved có Bỏ lưu; panel pins có Bỏ ghim cho Owner. Thao tác mutation hiển thị thành công sau REST, lỗi dùng lời nhắn an toàn.
- Chọn reply hiện thanh trích dẫn và Hủy trên composer; không xóa draft khi chọn/hủy quote, lỗi gửi giữ draft/quote. Tin reply hiển thị blockquote có accessible label; khi nhận edit/delete của tin gốc, cập nhật/gỡ quote. Canonical merge theo updatedAt và tombstone chặn dữ liệu cũ khôi phục quote/tin xóa.
- Preference chỉ hiển thị thay đổi sau xác nhận REST hoặc private invalidation; chưa tải được thì disabled và có nút Thử tải thông báo, âm thanh tạm tắt. Loading của preference tách riêng thao tác bỏ ghim/lưu để không khóa nút chuông. Không đưa SQL/exception/raw backend error vào UI.
- Realtime sử dụng cùng Channel transport và queue riêng của tài khoản; reconnect refresh các panel và preference. Tiếng ping chỉ khi tin mới của người khác tới Channel đang mở mà tab không focus, không ping edit/delete/history/own/duplicate. Âm thanh có thể bị browser chặn nếu chưa tương tác.

## DenHub — Thiết kế lại Tin nhắn ghim theo phong cách Zalo Web trên Discord Dark Mode (2026-11-12)

- **Vị trí hiển thị**: Thay vì mở Drawer riêng biệt, danh sách tin ghim chuyển thành banner inline nằm ngay dưới thanh tiêu đề Channel (`#general`) và bên trên khu vực nội dung chat. Cố định ở đầu khung chat, không bị cuộn theo lịch sử tin nhắn và không che khuất ô nhập tin.
- **Trạng thái thu gọn (mặc định)**: Chiều cao 50–52px, nền `#202234` bo góc 8px với viền siêu mỏng `rgba(255, 255, 255, 0.05)`, icon pin nghiêng `#818cf8`. Bố cục 2 dòng gọn gàng: dòng trên là tiêu đề `Tác giả · Tin nhắn đã ghim` (bold), dòng dưới là nội dung tin ghim (tự rút gọn `...`, bấm vào để nhảy tới tin gốc). Bên phải là nút `Xem tất cả ▾`. Khi không có tin ghim nào thì ẩn hoàn toàn banner.
- **Trạng thái mở rộng**: Nhấn `Xem tất cả ▾` mở rộng danh sách tin ghim với animation mượt mà (180ms), giới hạn `max-height: 220px` cuộn bên trong:
  - Mỗi hàng tin ghim gồm avatar người gửi, tên người gửi, thời gian gửi, nội dung tin nhắn và nút thao tác (Cuộn đến tin gốc, Bỏ ghim có Popconfirm xác nhận nếu có quyền Owner).
  - Phân cách bằng viền mỏng `rgba(255, 255, 255, 0.04)`, hover sáng nhẹ `rgba(99, 102, 241, 0.1)`.
  - Nút `Thu gọn ▴` để quay về trạng thái thanh ngang nhỏ gọn.
- **Tích hợp Channel Header**: Loại bỏ hàng toolbar riêng biệt phía trên Header. Toàn bộ công cụ được tích hợp trực tiếp vào góc phải Header:
  - Bên trái: `# general | Không Gian Của Tôi`.
  - Bên phải: Các icon có Tooltip tiếng Việt khi hover: 🔍 Tìm kiếm (`SearchOutlined`), 🔖 Tin đã lưu (`BookOutlined`), 🔔 Thông báo (`BellOutlined` / `BellFilled` hiển thị rõ trạng thái bật tím/tắt mờ), 👥 Thành viên (`TeamOutlined`), ⚙️ Cài đặt (`SettingOutlined`).
  - Drawer tìm kiếm và tin đã lưu mở trực tiếp khi bấm icon, không tạo thêm hàng điều hướng cồng kềnh.
- **Tương tác cuộn và highlight**: Nhấn vào tin ghim sẽ tự động cuộn lịch sử chat đến tin nhắn gốc và làm sáng nổi bật tin nhắn gốc trong 2 giây (`group-message-row--highlighted` với hiệu ứng viền cyan `#00f2fe` và pulse nền). Nếu tin nhắn chưa có trong bộ nhớ, tự động tải bù các trang lịch sử trước đó cho tới khi tìm thấy. Không che mất ô nhập tin nhắn phía dưới.
- **Responsive**: Trên mobile (<=600px), banner co gọn padding 12px, ẩn tên server dài ở header, giữ trọn vẹn icon và thao tác.

## DenHub — Cập nhật Logo thương hiệu mới "DEN" (2026-11-12)

- **Logo thương hiệu chính thức**: Tảng đá phát sáng cyan chứa chữ D và mạng lưới chat, kèm chữ EN phát sáng (`/den-logo.png`).
- **Nút Home trên thanh dock máy chủ (`MainLayout`)**: Thay thế icon tia sét tạm bằng logo DEN 48x48px bo góc 14-22px, viền cyan `#00F2FE` khi active, hiệu ứng chuyển đổi mượt mà.
- **Trang Đăng nhập / Đăng ký (`AuthLayout`)**: Thay thế SVG tay cầm game cũ bằng logo DEN 36x36px bo góc 10px kèm tiêu đề "DenHub".
- **Favicon trình duyệt (`index.html`)**: Trỏ icon về `/den-logo.png` đồng bộ trên tab trình duyệt.

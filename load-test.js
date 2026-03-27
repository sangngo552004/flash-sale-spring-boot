import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    // Giả lập 500 Virtual Users (VUs) liên tục gửi request trong 10 giây
    vus: 500,
    duration: '10s',
};

// Hàm random số để tạo userId và requestId ngẫu nhiên
function randomIntBetween(min, max) {
    return Math.floor(Math.random() * (max - min + 1) + min);
}

export default function () {
    const url = 'http://localhost:8080/api/flashsale/buy';
    
    // Tạo 1000 user khác nhau
    const userId = 'user_' + randomIntBetween(1, 1000);
    const productId = 2; // ID sản phẩm đem ra Flash Sale
    
    // Request ID ngẫu nhiên để test Idempotency (nếu bạn muốn test 1 user gửi trùng, hãy set cứng giá trị này)
    const requestId = 'req_' + Math.random().toString(36).substring(2, 15);

    const payload = {
        userId: userId,
        productId: productId
    };

    const params = {
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Request-ID': requestId,
        },
    };

    // Tạo body dạng form-data
    const body = `userId=${userId}&productId=${productId}`;

    // Bắn request POST
    const res = http.post(url, body, params);

    // Kiểm tra kết quả trả về
    check(res, {
        'Status is 200': (r) => r.status === 200,
        'Mua thành công': (r) => r.body.includes('ĐẶT HÀNG THÀNH CÔNG'),
        'Hết hàng': (r) => r.body.includes('HẾT HÀNG'),
        'Thao tác quá nhanh': (r) => r.body.includes('BẠN THAO TÁC QUÁ NHANH'),
        'Trùng lặp request': (r) => r.body.includes('BẠN ĐÃ ĐẶT ĐƠN NÀY RỒI'),
    });

    // Thêm một chút delay (10ms) để không làm cháy CPU máy tính của bạn khi test nội bộ
    sleep(0.01);
}

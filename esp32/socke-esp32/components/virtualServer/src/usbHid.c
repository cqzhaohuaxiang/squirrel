#include <stdlib.h>
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"


#include "tinyusb.h"
#include "class/hid/hid_device.h"
#include "virtualServer.h"

static const char *TAG = "UsbHid";

/**TinyUSB 描述符人机接口设备 （HID）：键盘、鼠标、通用 **/
#define TUSB_DESC_TOTAL_LEN      (TUD_CONFIG_DESC_LEN + CFG_TUD_HID * TUD_HID_DESC_LEN)
/**
    简短的 HID 报告描述符
    我们实现了键盘 + 鼠标 HID 设备、
    所以我们必须定义这两个报告描述符
 */
const uint8_t hid_report_descriptor[] = {
    TUD_HID_REPORT_DESC_KEYBOARD(HID_REPORT_ID(HID_ITF_PROTOCOL_KEYBOARD) ),
    TUD_HID_REPORT_DESC_MOUSE(HID_REPORT_ID(HID_ITF_PROTOCOL_MOUSE) )
};

/**制造描述符字符串*/
const char* hid_string_descriptor[5] = {
    (char[]){0x09, 0x04},   // 0: 支持的语言为英语 (0x0409)
    "Squirrel",              // 1: 制造商
    "Squirrel",             // 2: 产品
    "36870",               // 3: 序列号，应使用芯片 ID
    "USB-HID",            // 4: HID 接口
};

/**
 *配置描述符
 *这是一个简单的配置描述符，定义了 1 个配置和 1 个 HID 接口
 */
static const uint8_t hid_configuration_descriptor[] = {
    // 配置编号、接口数量、字符串索引、总长度、属性、功率（毫安)
    TUD_CONFIG_DESCRIPTOR(1, 1, 0, TUSB_DESC_TOTAL_LEN, TUSB_DESC_CONFIG_ATT_REMOTE_WAKEUP, 100),

    //这个描述符告诉主机如何与 HID 设备进行交互。它指定了设备的报告格式、端点地址和轮询间隔等信息。
    // 接口编号、字符串索引、启动协议、报告描述符长度、EP In 地址、大小和轮询间隔
    TUD_HID_DESCRIPTOR(0, 4, false, sizeof(hid_report_descriptor), 0x81, 16, 10),
};

/********* TinyUSB HID 回调程序***************/

// 收到 GET HID REPORT DESCRIPTOR 请求时调用
// 应用程序返回指向描述符的指针，描述符的内容必须存在足够长的时间才能完成传输
uint8_t const *tud_hid_descriptor_report_cb(uint8_t instance)
{
    // 我们只使用一个接口和一个 HID 报告描述符，因此可以忽略参数 "instance"。
    return hid_report_descriptor;
}

// 收到 GET_REPORT 控制请求时调用
// 应用程序必须填充缓冲区报告内容并返回其长度。
// 返回零值将导致堆栈中断请求
uint16_t tud_hid_get_report_cb(uint8_t instance, uint8_t report_id, hid_report_type_t report_type, uint8_t* buffer, uint16_t reqlen)
{
  (void) instance;
  (void) report_id;
  (void) report_type;
  (void) buffer;
  (void) reqlen;

  return 0;
}

// 当收到 SET_REPORT 控制请求或
// 在 OUT 端点上接收到数据 ( 报告 ID = 0, 类型 = 0 )
void tud_hid_set_report_cb(uint8_t instance, uint8_t report_id, hid_report_type_t report_type, uint8_t const* buffer, uint16_t bufsize)
{
}


void usbHidInit()
{
    const tinyusb_config_t tusb_cfg = {
        .device_descriptor = NULL,
        .string_descriptor = hid_string_descriptor,
        .string_descriptor_count = sizeof(hid_string_descriptor) / sizeof(hid_string_descriptor[0]),
        .external_phy = false,
        .configuration_descriptor = hid_configuration_descriptor,
    };

    ESP_ERROR_CHECK(tinyusb_driver_install(&tusb_cfg));
    ESP_LOGI(TAG, "USB 初始化完成");
  
}
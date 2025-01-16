#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_system.h"
#include "spi_flash_mmap.h"
#include "virtualServer.h"

void app_main(void)
{
    if (!create_data_queue()) {
        return;
    }
    wifiInit();  //WIFI 初始化
    usbHidInit(); //USB接口初始化为HID设备
    // 启动接收和发送任务
    xTaskCreate(&udp_receive_task, "udp_receive_task", 4096, NULL, 5, NULL);
    xTaskCreate(&udp_send_task, "udp_send_task", 4096, NULL, 5, NULL);
   
}

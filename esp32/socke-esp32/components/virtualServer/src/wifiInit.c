#include "virtualServer.h"
#include <string.h>
#include "esp_wifi.h"
#include "nvs_flash.h"
#include "esp_netif.h"


void wifiInit(){
    ESP_ERROR_CHECK(nvs_flash_init());
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());

    esp_netif_create_default_wifi_ap();//用户初始化默认AP（官方API）

	wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
	ESP_ERROR_CHECK(esp_wifi_init(&cfg));

	wifi_config_t wifi_config = {
		.ap = {
			.ssid = WIFI_SSID,
			.ssid_len = strlen(WIFI_SSID),
			.channel = 0,
			.password = WIFI_PASS,
			.max_connection = MAX_NUM,
			.authmode = WIFI_AUTH_WPA2_PSK,
			.pmf_cfg = {
				.required = false,
			},
		},
	};
	if (strlen(WIFI_PASS) == 0) {
		wifi_config.ap.authmode = WIFI_AUTH_OPEN;
	}

	ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_AP));
	ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &wifi_config));
	ESP_ERROR_CHECK(esp_wifi_start());
    
}
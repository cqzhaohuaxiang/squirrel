idf_component_register( SRC_DIRS "src"
                        INCLUDE_DIRS "include"	
                       #依赖IDF的文件目录
                        REQUIRES "spiffs"
                        REQUIRES "esp_wifi"
                        REQUIRES "nvs_flash"
                        REQUIRES "driver"
                        REQUIRES "esp_tinyusb"
                        REQUIRES "tinyusb"
                        )

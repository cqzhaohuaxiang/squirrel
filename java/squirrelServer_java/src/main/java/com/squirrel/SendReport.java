package com.squirrel;

import java.awt.Robot;
import java.util.Arrays;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.MouseInfo;
//长按由发送端实现    原生是由操作系统自行实现的但用Robot需自行实现
public class SendReport {

    private int keyDown [] = new int[6];  
    private int listKeys [] = new int[6];    
   
    void mouse(Robot robot, byte data[]){
        
        Point point = MouseInfo.getPointerInfo().getLocation();//获得鼠标当前位置
        robot.mouseMove(point.x + data[1] , point.y + data[2]);
            /**byte 0 字节内容*/
            StringBuffer bitString = new StringBuffer();
            bitString.append((data[0]>>7)&0x1)
                     .append((data[0]>>6)&0x1)
                     .append((data[0]>>5)&0x1)
                     .append((data[0]>>4)&0x1)
                     .append((data[0]>>3)&0x1)
                     .append((data[0]>>2)&0x1)
                     .append((data[0]>>1)&0x1)
                     .append((data[0]>>0)&0x1);
            // System.out.println("  "+ bitString.toString());   
            if(bitString.toString().charAt(7) == '1'){
                if (MouseButton.getLeft()==false) {
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    MouseButton.setLeftDown();
                    // 鼠标左键down 
                }
                     
            }
            if(bitString.toString().charAt(7) == '0'){
                if (MouseButton.getLeft()) {
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    MouseButton.setLeftUp();
                    //鼠标左键up
                }
                
            }
           
            if(bitString.toString().charAt(6) == '1'){
                if (MouseButton.getRight() == false) {
                    robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                    MouseButton.setRightDown();
                    //鼠标右键 down
                }
                
            }
            if(bitString.toString().charAt(6) == '0'){
                if (MouseButton.getRight()) {
                    robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                    MouseButton.setRightUp();
                    //鼠标右键 up     
                }      
            }    

            /**
             * 鼠标滚轮
             * 负值表示向上移动/远离用户
             * 正值表示向下移动/朝向用户
             * 
             * 与硬件相反
             * */
            if(data[3]>0){
                robot.mouseWheel( (~(data[3] - 1)));
            }else if(data[3]< 0){
        
                robot.mouseWheel(~(data[3] - 1));
            }
    }

    // 检查 byte 变量的指定位是否为 1
    private static boolean isBitSet(byte value, int bitPosition) {
        if (bitPosition < 0 || bitPosition > 7) {
            throw new IllegalArgumentException("bitPosition must be between 0 and 7");
        }
        // 创建掩码，将指定位设置为 1
        int mask = 1 << bitPosition;
        // 使用掩码与值进行按位与运算，检查结果是否非零
        return (value & mask) != 0;
    }

    void keyboard(Robot robot, byte data[]){
    
        // 功能键按下       0 Ctrl    1 Shift    3 WIN    6 右边Alt
        if (isBitSet(data[0], 0)) {robot.keyPress(KeyEvent.VK_CONTROL); }
        if (isBitSet(data[0], 1)) {robot.keyPress(KeyEvent.VK_SHIFT); }
        if (isBitSet(data[0], 3)) {robot.keyPress(KeyEvent.VK_WINDOWS); }
        if (isBitSet(data[0], 6)) {robot.keyPress(KeyEvent.VK_ALT);}
        //复制出按键字段
        byte keys [] = new byte[6];
        System.arraycopy(data, 2, keys, 0, keys.length);

        Arrays.fill(keyDown, 0); //清空数组
        //将USB-HID 报告符数据变为Robot 的常量保存  
        for(int i =0;i<keys.length;i++){
            switch (keys[i]) {
                case 0x04: 
                    robot.keyPress(KeyEvent.VK_A); 
                    keyDown[i] = KeyEvent.VK_A;
                    break;
                case 0x05: 
                    robot.keyPress(KeyEvent.VK_B); 
                    keyDown[i] = KeyEvent.VK_B;
                    break;
                case 0x06: 
                    robot.keyPress(KeyEvent.VK_C); 
                    keyDown[i] = KeyEvent.VK_C;
                    break;
                case 0x07: 
                    robot.keyPress(KeyEvent.VK_D); 
                    keyDown[i] = KeyEvent.VK_D;
                    break;
                case 0x08: 
                    robot.keyPress(KeyEvent.VK_E); 
                    keyDown[i] = KeyEvent.VK_E;
                    break;
                case 0x09: 
                    robot.keyPress(KeyEvent.VK_F); 
                    keyDown[i] = KeyEvent.VK_F;
                    break;
                case 0X0A: 
                    robot.keyPress(KeyEvent.VK_G); 
                    keyDown[i] = KeyEvent.VK_G;
                    break;
                case 0x0B: 
                    robot.keyPress(KeyEvent.VK_H); 
                    keyDown[i] = KeyEvent.VK_H;
                    break;
                case 0x0C: 
                    robot.keyPress(KeyEvent.VK_I); 
                    keyDown[i] = KeyEvent.VK_I;
                    break;
                case 0x0D: 
                    robot.keyPress(KeyEvent.VK_J); 
                    keyDown[i] = KeyEvent.VK_J;
                    break;
                case 0x0E: 
                    robot.keyPress(KeyEvent.VK_K); 
                    keyDown[i] = KeyEvent.VK_K;
                    break;
                case 0x0F: 
                    robot.keyPress(KeyEvent.VK_L); 
                    keyDown[i] = KeyEvent.VK_L;
                    break;
                 case 0x10: //M
                    robot.keyPress(KeyEvent.VK_M); 
                    keyDown[i] = KeyEvent.VK_M;
                    break;
                case 0x11: //N
                    robot.keyPress(KeyEvent.VK_N); 
                    keyDown[i] = KeyEvent.VK_N;
                    break;
                case 0x12: //O
                    robot.keyPress(KeyEvent.VK_O); 
                    keyDown[i] = KeyEvent.VK_O;
                    break;
                case 0x13: //P
                    robot.keyPress(KeyEvent.VK_P); 
                    keyDown[i] = KeyEvent.VK_P;
                    break;
                case 0x14: //Q
                    robot.keyPress(KeyEvent.VK_Q); 
                    keyDown[i] = KeyEvent.VK_Q;
                    break;
                case 0x15: //R
                    robot.keyPress(KeyEvent.VK_R);    
                    keyDown[i] = KeyEvent.VK_R;
                    break;
                case 0x16: //S
                    robot.keyPress(KeyEvent.VK_S); 
                    keyDown[i] = KeyEvent.VK_S;
                    break;
                case 0x17: 
                    robot.keyPress(KeyEvent.VK_T); 
                    keyDown[i] = KeyEvent.VK_T;
                    break;
                case 0x18: 
                    robot.keyPress(KeyEvent.VK_U); 
                    keyDown[i] = KeyEvent.VK_U;
                    break;
                case 0x19: 
                    robot.keyPress(KeyEvent.VK_V); 
                    keyDown[i] = KeyEvent.VK_V;
                    break;
                case 0x1A:
                    robot.keyPress(KeyEvent.VK_W); 
                    keyDown[i] = KeyEvent.VK_W;
                    break;
                case 0x1B: 
                    robot.keyPress(KeyEvent.VK_X); 
                    keyDown[i] = KeyEvent.VK_X;
                    break;
                case 0x1C: 
                    robot.keyPress(KeyEvent.VK_Y); 
                    keyDown[i] = KeyEvent.VK_Y;
                    break;
                case 0x1D: 
                    robot.keyPress(KeyEvent.VK_Z); 
                    keyDown[i] = KeyEvent.VK_Z;
                    break;
                case 0x1E: 
                    robot.keyPress(KeyEvent.VK_1); 
                    keyDown[i] = KeyEvent.VK_1;
                    break;
                case 0x1F: 
                    robot.keyPress(KeyEvent.VK_2); 
                    keyDown[i] = KeyEvent.VK_2;
                    break;
                case 0x20: 
                    robot.keyPress(KeyEvent.VK_3); 
                    keyDown[i] = KeyEvent.VK_3;
                    break;
                case 0x21:
                    robot.keyPress(KeyEvent.VK_4); 
                    keyDown[i] = KeyEvent.VK_4;
                    break;
                case 0x22: //5
                    robot.keyPress(KeyEvent.VK_5); 
                    keyDown[i] = KeyEvent.VK_5;
                    break;
                case 0x23: //6
                    robot.keyPress(KeyEvent.VK_6);
                    keyDown[i] = KeyEvent.VK_6;
                    break;
                case 0x24: //7
                    robot.keyPress(KeyEvent.VK_7); 
                    keyDown[i] = KeyEvent.VK_7;
                    break;
                case 0x25: //8
                    robot.keyPress(KeyEvent.VK_8); 
                    keyDown[i] = KeyEvent.VK_9;
                    break;
                case 0x26: //9
                    robot.keyPress(KeyEvent.VK_9); 
                    keyDown[i] = KeyEvent.VK_9;
                    break;
                case 0x27: //0
                    robot.keyPress(KeyEvent.VK_0); 
                    keyDown[i] = KeyEvent.VK_0;
                    break;
                case 0x28: 
                    robot.keyPress(KeyEvent.VK_ENTER); 
                    keyDown[i] = KeyEvent.VK_ENTER;
                    break;
                case 0x29: //ESC
                    robot.keyPress(KeyEvent.VK_ESCAPE); 
                    keyDown[i] = KeyEvent.VK_ESCAPE;
                    break;
                case 0x2A: 
                    robot.keyPress(KeyEvent.VK_BACK_SPACE); 
                    keyDown[i] = KeyEvent.VK_BACK_SPACE;
                    break;
                case 0x2B: //TAB
                    robot.keyPress(KeyEvent.VK_TAB); 
                    keyDown[i] = KeyEvent.VK_TAB;
                    break;
                case 0x2C: 
                    robot.keyPress(KeyEvent.VK_SPACE); 
                    keyDown[i] = KeyEvent.VK_SPACE;
                    break;
                case 0x2D: // -_
                    robot.keyPress(KeyEvent.VK_MINUS); 
                    keyDown[i] = KeyEvent.VK_MINUS;
                    break;
                case 0x2E: // + =
                    robot.keyPress(KeyEvent.VK_EQUALS); 
                    keyDown[i] = KeyEvent.VK_EQUALS;
                    break;
                case 0x2F: // { [ 
                    robot.keyPress(KeyEvent.VK_OPEN_BRACKET); 
                    keyDown[i] = KeyEvent.VK_OPEN_BRACKET;
                    break;
                case 0x30: // } ]
                    robot.keyPress(KeyEvent.VK_CLOSE_BRACKET); 
                    keyDown[i] = KeyEvent.VK_CLOSE_BRACKET;
                    break;
                case 0x31: // \ |
                    robot.keyPress(KeyEvent.VK_BACK_SLASH); 
                    keyDown[i] = KeyEvent.VK_BACK_SLASH;
                    break;
                 case 0x33: // ;:
                    robot.keyPress(KeyEvent.VK_SEMICOLON); 
                    keyDown[i] = KeyEvent.VK_SEMICOLON;
                    break;
                case 0x34: 
                    robot.keyPress(KeyEvent.VK_QUOTE); 
                    keyDown[i] = KeyEvent.VK_QUOTE;
                    break;
                case 0x35: 
                    robot.keyPress(KeyEvent.VK_BACK_QUOTE); 
                    keyDown[i] = KeyEvent.VK_BACK_QUOTE;
                    break;
                case 0x36: // <, 
                    robot.keyPress(KeyEvent.VK_COMMA); 
                    keyDown[i] = KeyEvent.VK_COMMA;
                    break;
                case 0x37: // >.
                    robot.keyPress(KeyEvent.VK_PERIOD); 
                    keyDown[i] = KeyEvent.VK_PERIOD;
                    break;
                case 0x38: 
                    robot.keyPress(KeyEvent.VK_SLASH); 
                    keyDown[i] = KeyEvent.VK_SLASH;
                    break;
                case 0x39: 
                    robot.keyPress(KeyEvent.VK_CAPS_LOCK); 
                    keyDown[i] = KeyEvent.VK_CAPS_LOCK;
                    break;
                case 0x3A: //F1
                    robot.keyPress(KeyEvent.VK_F1); 
                    keyDown[i] = KeyEvent.VK_F1;
                    break;
                case 0x3B: 
                    robot.keyPress(KeyEvent.VK_F2); 
                    keyDown[i] = KeyEvent.VK_F2;
                    break;
                case 0x3C: //F3
                    robot.keyPress(KeyEvent.VK_F3); 
                    keyDown[i] = KeyEvent.VK_F3;
                    break;
                case 0x3D: //F4
                    robot.keyPress(KeyEvent.VK_F4); 
                    keyDown[i] = KeyEvent.VK_F4;
                    break;
                case 0x3E: //F5
                    robot.keyPress(KeyEvent.VK_F5); 
                    keyDown[i] = KeyEvent.VK_F5;
                    break;
                case 0x3F: //F6
                    robot.keyPress(KeyEvent.VK_F6); 
                    keyDown[i] = KeyEvent.VK_F6;  
                    break;        
                case 0x40: //F7
                    robot.keyPress(KeyEvent.VK_F7); 
                    keyDown[i] = KeyEvent.VK_F7;
                    break;
                case 0x41: //F8
                    robot.keyPress(KeyEvent.VK_F8); 
                    keyDown[i] = KeyEvent.VK_F8;
                    break;
                case 0x42: //F9
                    robot.keyPress(KeyEvent.VK_F9); 
                    keyDown[i] = KeyEvent.VK_F9;
                    break;
                case 0x43: //F10
                    robot.keyPress(KeyEvent.VK_F10); 
                    keyDown[i] = KeyEvent.VK_F10;
                    break;
                case 0x44: //F11
                    robot.keyPress(KeyEvent.VK_F11); 
                    keyDown[i] = KeyEvent.VK_F11;
                    break;
                case 0x45: //F12
                    robot.keyPress(KeyEvent.VK_F12); 
                    keyDown[i] = KeyEvent.VK_F12;
                    break;
                case 0x4C: 
                    robot.keyPress(KeyEvent.VK_DELETE); 
                    keyDown[i] = KeyEvent.VK_DELETE;
                    break;
                case 0x4F: 
                    robot.keyPress(KeyEvent.VK_RIGHT); 
                    keyDown[i] = KeyEvent.VK_RIGHT;
                    break;
                case 0x50: 
                    robot.keyPress(KeyEvent.VK_LEFT); 
                    keyDown[i] = KeyEvent.VK_LEFT;
                    break;
                 case 0x51: 
                    robot.keyPress(KeyEvent.VK_DOWN); 
                    keyDown[i] = KeyEvent.VK_DOWN;
                    break;
                case 0x52: 
                    robot.keyPress(KeyEvent.VK_UP); 
                    keyDown[i] = KeyEvent.VK_UP;
                    break;
            }
      
        }
        
        /**
            * keyDown 为这次的报告符数据 （java 的常量值）
            * listKeys 为上一次的报告符数据 （java 的常量值）
        */

        if (keyDown[0] == 0) {
            //当这次发送的是0时 ，按键抬起了   
            for(int i=0; i<listKeys.length;i++ ){
                if(listKeys[i] != 0){
                    robot.keyRelease(listKeys[i]);    //这个操作好像没有什么卵用  
                }    
            }
           
        }
        //这一次的按键存入上次中去
        System.arraycopy(keyDown, 0, listKeys, 0, keyDown.length);

        // 功能键抬起
        if (!isBitSet(data[0], 0)) {robot.keyRelease(KeyEvent.VK_CONTROL);}
        if (!isBitSet(data[0], 1)) {robot.keyRelease(KeyEvent.VK_SHIFT);}
        if (!isBitSet(data[0], 3)) {robot.keyRelease(KeyEvent.VK_WINDOWS);}
        if (!isBitSet(data[0], 6)) {robot.keyRelease(KeyEvent.VK_ALT);}

    }

   


    
}

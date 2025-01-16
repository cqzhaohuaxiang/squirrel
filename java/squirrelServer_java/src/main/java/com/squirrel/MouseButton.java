
package com.squirrel;

public class MouseButton {
    private static boolean left;
    private static boolean right;
    public static  void setLeftDown(){
        left = true;
    }
    public static void setRightDown(){
        right = true;
    }

    public static void setLeftUp(){
        left = false;
    }
    public static void setRightUp(){
        right = false;
    }
    public static boolean getLeft(){
        return left;
    }
    public static boolean getRight(){
        return right;
    }
}

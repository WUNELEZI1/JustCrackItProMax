package com.jck.promax;

/**
 * 字符串混淆工具——防止反编译直接看到敏感字符串明文
 * 
 * 原理：每个敏感字符串用独立的 XOR 密钥加密为字节数组，
 * 运行时再还原。反编译只能看到乱数字节数组，看不到原始字符串。
 * 
 * 每个字符串使用不同的 XOR 密钥，即使一个密钥被破解也不影响其他。
 * 新版增加了多种异或模式：偏移异或、双密钥交错、三密钥循环、倒序双密钥等。
 */
public class Obfuscator {

    /**
     * 使用指定密钥对字节数组进行 XOR 解密
     * 每字节与密钥异或后还原原文
     */
    public static String dec(byte[] obfuscated, byte key) {
        byte[] result = new byte[obfuscated.length];
        for (int i = 0; i < obfuscated.length; i++) {
            result[i] = (byte) (obfuscated[i] ^ key);
        }
        return new String(result);
    }

    /**
     * 双密钥 XOR 解密——增加反编译难度
     * 交替使用 key1 和 key2 异或
     */
    public static String dec2(byte[] obfuscated, byte key1, byte key2) {
        byte[] result = new byte[obfuscated.length];
        for (int i = 0; i < obfuscated.length; i++) {
            result[i] = (byte) (obfuscated[i] ^ (i % 2 == 0 ? key1 : key2));
        }
        return new String(result);
    }

    /**
     * 三段式 XOR 解密——最高强度
     * 三组密钥 XY 循环使用
     */
    public static String dec3(byte[] obfuscated, byte k1, byte k2, byte k3) {
        byte[] result = new byte[obfuscated.length];
        byte[] keys = {k1, k2, k3};
        for (int i = 0; i < obfuscated.length; i++) {
            result[i] = (byte) (obfuscated[i] ^ keys[i % 3]);
        }
        return new String(result);
    }

    /**
     * 拼接式解密——将字符串拆成多段分别混淆
     * 第一段用 key1 解密，后续段用 key2 解密，然后拼接
     */
    public static String decCat(byte[] part1, byte key1, byte[] part2, byte key2) {
        return dec(part1, key1) + dec(part2, key2);
    }

    /**
     * 三段拼接解密
     */
    public static String decCat3(byte[] p1, byte k1, byte[] p2, byte k2, byte[] p3, byte k3) {
        return dec(p1, k1) + dec(p2, k2) + dec(p3, k3);
    }

    /**
     * 偏移异或解密——每个字节使用不同的密钥偏移
     * 第 i 字节用 (key + i) 异或，防止相同字符产生相同密文
     */
    public static String decOff(byte[] obfuscated, byte baseKey) {
        byte[] result = new byte[obfuscated.length];
        for (int i = 0; i < obfuscated.length; i++) {
            result[i] = (byte) (obfuscated[i] ^ (baseKey + i));
        }
        return new String(result);
    }

    /**
     * 倒序双密钥解密——先按 key1 解密后反转，再按 key2 解密
     * 破坏反编译工具的直接模式匹配
     */
    public static String decRev(byte[] obfuscated, byte key1, byte key2) {
        byte[] step1 = new byte[obfuscated.length];
        for (int i = 0; i < obfuscated.length; i++) {
            step1[i] = (byte) (obfuscated[i] ^ key1);
        }
        // 反转
        byte[] reversed = new byte[step1.length];
        for (int i = 0; i < step1.length; i++) {
            reversed[i] = step1[step1.length - 1 - i];
        }
        byte[] result = new byte[reversed.length];
        for (int i = 0; i < reversed.length; i++) {
            result[i] = (byte) (reversed[i] ^ key2);
        }
        return new String(result);
    }

    /**
     * 混合解密——组合使用多种解密方式
     * 先用 dec 解密，再从中提取有效部分
     * 可用于在字符串中嵌入垃圾字节混淆
     */
    public static String decMix(byte[] obfuscated, byte key, int start, int end) {
        byte[] full = new byte[obfuscated.length];
        for (int i = 0; i < obfuscated.length; i++) {
            full[i] = (byte) (obfuscated[i] ^ key);
        }
        String raw = new String(full);
        if (end > start && start >= 0 && end <= raw.length()) {
            return raw.substring(start, end);
        }
        return raw;
    }
}
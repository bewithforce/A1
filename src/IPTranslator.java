public class IPTranslator {
    public static void main(String[] args) throws Exception {
        String ip = "255.255.255.255";
        System.out.println(intToIp(-1));
        System.out.println(ipToInt(ip));
    }

    public static String intToIp(int encrypted) {
        StringBuilder decrypted = new StringBuilder(15);
        decrypted.append((encrypted & -16777216) >>> 24); // -16777216 ~ FF000000 т.о. получается значение первого байта числа
        decrypted.append('.');
        decrypted.append((encrypted & 16711680) >>> 16); // 16711680 ~ FF0000
        decrypted.append('.');
        decrypted.append((encrypted & 65280) >>> 8); // 65280 ~ FF00
        decrypted.append('.');
        decrypted.append(encrypted & 255); // 255 ~ FF
        return decrypted.toString();
    }

    public static int ipToInt(String ip) throws Exception {
        if(!ip.matches("\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}")){
            throw new Exception("bad ip");
        }
        int encrypted = 0;
        var arr = ip.split("\\.");
        for (int i = 0; i < arr.length - 1; i++) {
            encrypted += Short.parseShort(arr[i]);
            encrypted <<= 8;
        }
        encrypted += Short.parseShort(arr[arr.length - 1]);
        return encrypted;
    }
}

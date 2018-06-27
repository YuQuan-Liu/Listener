package com.rocket.utils;

public class StringUtil {

	/**
	 * 字符串到byte[]  集中器地址  表地址时
	 * @param str
	 * @return byte[]
	 */
	public static byte[] string2Byte(String str){
		
		int len = str.length()/2;
		
		String[] xx = new String[len];
		byte[] addr = new byte[len];
		
		for(int i=0;i<len;i++){
			xx[i] = str.substring(i*2,i*2+2);
			addr[i] = (byte) Integer.parseInt(xx[i], 16);
		}
		
		return addr;
	}
	

	/**
	 * 返回的指令拼接成字符串  中间加空格
	 * @param b 需要拼接的byte[]
	 * @param size   需要拼接的长度
	 * @return
	 */
	public static String byteArrayToHexStr(byte[] b,int size){
		StringBuilder sb = new StringBuilder();
		for(int i = 0;i < size;i++){
			sb.append(String.format("%02x", b[i]&0xFF)+" ");
		}
		return sb.toString();
	}
	
	/**
	 * 返回的指令拼接成字符串  中间不加空格
	 * @param b 需要拼接的byte[]
	 * @param size   需要拼接的长度
	 * @return
	 */
	public static String byteArrayToHexStrNonspace(byte[] b,int size){
		StringBuilder sb = new StringBuilder();
		for(int i = 0;i < size;i++){
			sb.append(String.format("%02x", b[i]&0xFF));
		}
		return sb.toString();
	}
}

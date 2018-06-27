package com.rocket.utils;


import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class Property {
	
	private static Properties props = new Properties();
	static{
//		ClassLoader loader = Property.class.getClassLoader();
//		InputStream is = loader.getResourceAsStream("rocket.properties");
		
		try{
			InputStream is = new FileInputStream(System.getProperty("user.dir")+"/rocket.properties");
			props.load(is);
			is.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public static String getValue(String key){
		return props.getProperty(key);
	}
	
}
package com.rocket.readmeter.obj;

import java.math.BigDecimal;

public class CustomerWarn {

	private int cid;
	private String customerName;
	private BigDecimal customerBalance;
	private String customerMobile;
	private String customerEmail;
	private int warnStyle;
	private String companyName;
	public int getCid() {
		return cid;
	}
	public void setCid(int cid) {
		this.cid = cid;
	}
	public String getCustomerName() {
		return customerName;
	}
	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}
	public BigDecimal getCustomerBalance() {
		return customerBalance;
	}
	public void setCustomerBalance(BigDecimal customerBalance) {
		this.customerBalance = customerBalance;
	}
	public String getCustomerMobile() {
		return customerMobile;
	}
	public void setCustomerMobile(String customerMobile) {
		this.customerMobile = customerMobile;
	}
	public String getCustomerEmail() {
		return customerEmail;
	}
	public void setCustomerEmail(String customerEmail) {
		this.customerEmail = customerEmail;
	}
	public int getWarnStyle() {
		return warnStyle;
	}
	public void setWarnStyle(int warnStyle) {
		this.warnStyle = warnStyle;
	}
	public String getCompanyName() {
		return companyName;
	}
	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}
	public CustomerWarn() {
		super();
	}
	@Override
	public String toString() {
		return "CustomerWarn [cid=" + cid + ", customerName=" + customerName
				+ ", customerBalance=" + customerBalance + ", customerMobile="
				+ customerMobile + ", customerEmail=" + customerEmail
				+ ", warnStyle=" + warnStyle + ", companyName=" + companyName
				+ "]";
	}
	
	
}

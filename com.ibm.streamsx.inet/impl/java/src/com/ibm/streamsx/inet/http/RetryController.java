//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

class RetryController {
	
	protected int maxRetries = 3;
	protected int sleepDelay = 30;
	protected int curTry = 0;
	
	public RetryController(int maxRetries, int sleepDelay) {
		this.maxRetries = maxRetries;
		this.sleepDelay = sleepDelay;
	}
	
	void connectionSuccess() {
		//reset();
	}
	void connectionClosed() {
		increment();
	}
	void readException() {
		increment();
	}
	void readSuccess() {
		reset();
	}
	
	boolean doRetry() {
		return maxRetries == -1 || curTry <= maxRetries;
	}
	int getSleep() {
		return sleepDelay;
	}	
	protected void reset() {
		curTry = 0;
	}
	protected void increment() {
		curTry++;
	}
}

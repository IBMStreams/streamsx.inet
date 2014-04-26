//
// *******************************************************************************
// * Copyright (C)2014, International Business Machines Corporation and *
// * others. All Rights Reserved. *
// *******************************************************************************
//
package com.ibm.streamsx.inet.http;

class BackoffRetryController extends RetryController {
	
	private double curSleepDelay = 0;
	private int factor = 10;
	
	public BackoffRetryController(int maxRetries, double sleepDelay) {
		super(maxRetries, sleepDelay);
		this.curSleepDelay = sleepDelay;
	}
	
	@Override
	protected void reset() {
		super.reset();
		this.curSleepDelay = sleepDelay;
	}
	@Override
	protected void increment() {
		if(curTry > 0)
			curSleepDelay = curSleepDelay * factor;
		super.increment();
	}
	
	@Override
	double getSleep() {
		return curSleepDelay;
	}	

}

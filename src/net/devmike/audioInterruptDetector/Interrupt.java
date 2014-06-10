package net.devmike.audioInterruptDetector;

public class Interrupt
{
	public final long startTime;
	public final long endTime;
	
	public Interrupt(long startTime, long endTime)
	{
		this.startTime = startTime;
		this.endTime   = endTime;
	}
}

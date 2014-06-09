package net.devmike;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class AudioVisualizer
{
	private static final int SCREEN_WIDTH  = 1600;
	private static final int SCREEN_HEIGHT = 500;
	
	//private static final int GRAPH_X_RANGE = SCREEN_WIDTH - 20;
	private static final int GRAPH_Y_RANGE = SCREEN_HEIGHT / 2 - 50;
	
	
	
	private AudioVisualizerPanel panel;
	private JScrollPane scroller;
	private int scrollPosX = 0;
	
	public AudioVisualizer()
	{
		JFrame frame = new JFrame("AudioVisualizer");
		
		frame.setBackground(Color.BLACK);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		panel = new AudioVisualizerPanel();
		frame.getContentPane().add(panel, java.awt.BorderLayout.CENTER);
		
		scroller = new JScrollPane(panel);
		scroller.getHorizontalScrollBar().addAdjustmentListener(new AdjustmentListener()
		{
			public void adjustmentValueChanged(AdjustmentEvent e)
			{
				scrollPosX = e.getValue();
				scroller.repaint();
			}
		});
		frame.getContentPane().add(scroller, java.awt.BorderLayout.CENTER);
		
		frame.pack();
		frame.setVisible(true);
	}
	
	private class AudioVisualizerPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;
		
		public AudioVisualizerPanel()
		{
			super();
			
			setBackground(Color.BLUE);
			setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
		}
		
		public void paint(Graphics g)
		{
			drawGraph(g);
		}
	}
	
	
	
	
	
	private class Reading
	{
		public static final int MAX_VALUE = (int)Short.MAX_VALUE;
		//public static final int MIN_VALUE = -MAX_VALUE; // value is ALMSOT a short, but it's min value is actually one less (why?)
		
		public final long time;
		public final short value;
		
		public Reading(long time, short value)
		{
			this.time = time;
			this.value = value;
		}
	}
	
	private class Interrupt
	{
		public final long startTime;
		public final long endTime;
		
		public Interrupt(long startTime, long endTime)
		{
			this.startTime = startTime;
			this.endTime   = endTime;
		}
	}
	
	
	
	
	
	
	private ArrayList<Reading>   rawReadingsDisplay     = new ArrayList<Reading>();
	private ArrayList<Reading>   averageReadingsDisplay = new ArrayList<Reading>();
	private ArrayList<Interrupt> interruptsDisplay      = new ArrayList<Interrupt>();
	
	private long displayRelativeTime = -Long.MIN_VALUE;
	
	private int displayWidth = 0;
	
	private static final double DATA_X_SCALE = 0.1;
	private static final double DATA_Y_SCALE = (double)GRAPH_Y_RANGE / Reading.MAX_VALUE;
	
	
	
	
	// data
	private static final int NUM_READINGS_TO_AVERAGE = 10; // number of points to average together
	
	private static final int INTERRUPT_THRESHOLD       = Reading.MAX_VALUE / 2;
	private static final int MIN_INTERRUPT_START_SLOPE = (Reading.MAX_VALUE / 2) / 20;
	private static final int MIN_INTERRUPT_DURATION    = 100;
	private static final int MAX_INTERRUPT_DURATION    = 2000;
	
	private static final int NUM_BACKFILL_AVERAGE_READINGS = 5;
	
	// process data
	private long time = Long.MIN_VALUE;
	
	private byte leftoverAudioDataByte;
	private boolean useLeftoverAudioDataByte = false;
	
	private Reading[] leftoverRawReadings     = new Reading[0];
	private Reading[] backfillAverageReadings = new Reading[0];
	
	private int backfillAverageReadingsSkip = 0; // number of average readings to skip due to the backfill
	
	private boolean insideInterrupt = false;
	private long interruptStartTime = 0;
	private int interruptEndReadingIndex = -1;
	private int interruptSign = 0; // sign of the interrupt's start value (1 or -1). defines what side the "wave" is on
	
	public int processAudioData(byte[] data, int offset, int dataLength)
	{
		return processAudioData(data, offset, dataLength, true);
	}
	public int processAudioData(byte[] data, int dataOffset, int dataLength, boolean bigEndian)
	{
		if (rawReadingsDisplay.size() > 500000) {
			displayRelativeTime = -rawReadingsDisplay.get(rawReadingsDisplay.size() - 1).time;
			rawReadingsDisplay.clear();
			averageReadingsDisplay.clear();
			interruptsDisplay.clear();
		}
		
		int numInterrupts = 0;
		
		// because we need two bytes for every reading, the data length must be kept even
		int evenDataLength = dataLength;
		
		if (useLeftoverAudioDataByte) // account for the offset of using the leftover byte
			++evenDataLength;
		
		if (evenDataLength % 2 == 1)
			--evenDataLength;
		
		
		int numRawReadingsWillBeCreated     = evenDataLength / 2;                                        // number of raw readings that will be created
		int totalNumRawReadings             = leftoverRawReadings.length + numRawReadingsWillBeCreated;  // total number of raw readings (leftover and will be created)
		int numAverageReadingsWillBeCreated = totalNumRawReadings / NUM_READINGS_TO_AVERAGE;             // number of average readings that will be created (rounded down)
		int numRawReadingsWillBeUsed        = numAverageReadingsWillBeCreated * NUM_READINGS_TO_AVERAGE; // number of raw readings that will be used
		
		
		// create an array for the raw readings
		int numRawReadings = 0;
		Reading[] rawReadings = new Reading[numRawReadingsWillBeUsed];
		
		// add the leftover raw readings
		if (numRawReadingsWillBeUsed > 0) // only add the leftovers if they are going to be used
		{
			for (int i = 0; i < leftoverRawReadings.length; ++i)
				rawReadings[i] = leftoverRawReadings[i];
			
			numRawReadings = leftoverRawReadings.length;
		}
		
		// create an array for holding the leftover raw readings
		Reading[] leftoverRawReadingsLast = leftoverRawReadings;
		
		int numLeftoverRawReadings = 0;
		leftoverRawReadings = new Reading[totalNumRawReadings - numRawReadingsWillBeUsed];
		
		// none of the raw readings are being used, which means none of the leftovers will be used
		if (numRawReadingsWillBeUsed == 0)
		{
			// copy the leftovers to the new array
			for (int i = 0; i < leftoverRawReadingsLast.length; ++i)
				leftoverRawReadings[i] = leftoverRawReadingsLast[i];
			
			numLeftoverRawReadings = leftoverRawReadingsLast.length;
		}
		
		
		// iterate through the data bytes and create the raw readings
		for (int i = 0; i < evenDataLength; i += 2)
		{
			// select the bytes to use
			byte byte1;
			byte byte2;
			
			// check if we should use the leftover byte
			if (useLeftoverAudioDataByte)
			{
				// use the leftover byte and the first byte
				byte1 = leftoverAudioDataByte;
				byte2 = data[dataOffset];
				
				// don't use the leftover byte anymore
				useLeftoverAudioDataByte = false;
				
				// decrement the index to offset using the leftover byte
				--i;
			}
			
			// use the normal bytes based on the index
			else
			{
				byte1 = data[i + dataOffset];
				byte2 = data[i + dataOffset + 1];
			}
			
			
			// convert the two bytes to a single short
			// Remember! The data types are signed! (and no way to use unsigned... grumble grumble)
			
			// For the examples bellow I will be using the following bytes:
			// 00110010 10110101 (12981)
			
			// first we convert the signed bytes, to signed shorts
			// short1: 00000000 00110010 (50)
			// short2: 11111111 10110101 (-75)
			
			// data
			short short1;
			short short2;
			
			if (bigEndian)
			{
				short1 = byte1;
				short2 = byte2;
			}
			else
			{
				// if bytes are in little-endian, the most significant bit is last and we must reverse the order
				short2 = byte1;
				short1 = byte2;
			}
			
			// shift the most significant byte over 8 bits
			short1 <<= 8;
			//    00000000 00110010
			// << 8
			//    -----------------
			//    00110010 00000000
			
			// mask the least significant byte so all bits are 0 except the last 8. This is necessary because the value is negative.
			// Therefore, when converting from signed byte to signed short, the extra 8 bits that are added to the beginning are 1's in order to keep the value the same and negative.
			short2 &= 0b00000000_11111111;
			//   11111111 10110101
			// & 00000000 11111111
			//   -----------------
			//   00000000 10110101
			
			// finally, combine the two shorts with a bitwise OR
			short dataVal = (short)(short1 | short2);
			//   00110010 00000000
			// | 00000000 10110101
			//   -----------------
			//   00110010 10110101
			
			Reading rawReading = new Reading(time++, dataVal);
			
			// check if we have all the raw readings we are going to use
			if (numRawReadings == numRawReadingsWillBeUsed)
				leftoverRawReadings[numLeftoverRawReadings++] = rawReading; // put in leftover array
			else
				rawReadings[numRawReadings++] = rawReading; // put in normal array
			
			rawReadingsDisplay.add(rawReading);
		}
		
		
		// create an array for holding the average readings
		int numAverageReadings = 0;
		Reading[] averageReadings = new Reading[backfillAverageReadings.length + numAverageReadingsWillBeCreated];
		
		// add the backfill average readings
		for (int i = 0; i < backfillAverageReadings.length; ++i)
			averageReadings[i] = backfillAverageReadings[i];
		
		numAverageReadings = backfillAverageReadings.length;
		
		// create the average readings from the raw readings
		for (int i = 0; i < numAverageReadingsWillBeCreated; ++i)
		{
			int totalValue = 0;
			
			for (int j = 0; j < NUM_READINGS_TO_AVERAGE; ++j)
				totalValue += rawReadings[i * NUM_READINGS_TO_AVERAGE + j].value;
			
			Reading averageReading = new Reading(rawReadings[i * NUM_READINGS_TO_AVERAGE].time, (short)(totalValue / NUM_READINGS_TO_AVERAGE));
			averageReadings[numAverageReadings++] = averageReading;
			
			averageReadingsDisplay.add(averageReading);
		}
		
		
		// look for interrupts
		for (int i = backfillAverageReadingsSkip; i < averageReadings.length; ++i)
		{
			Reading reading = averageReadings[i];
			
			// check if we are already inside an interrupt
			if (insideInterrupt)
			{
				// check if we are below the interrupt threshold. Using interruptSign instead of Math.abs accounts for dramatic shifts from one sign to the other 
				if (reading.value * interruptSign > INTERRUPT_THRESHOLD)
				{
					// we are above the threshold, the interrupt is not ending
					interruptEndReadingIndex = -1;
					
					// don't go over the max interrupt length
					if (reading.time - interruptStartTime > MAX_INTERRUPT_DURATION)
					{
						// reset
						insideInterrupt = false;
						interruptStartTime = 0;
						interruptEndReadingIndex = -1;
					}
				}
				else
				{
					// we are below the threshold, the interrupt MAY be ending
					// set the end if it has not been set yet
					if (interruptEndReadingIndex == -1)
						interruptEndReadingIndex = i;
					else
					{
						// the end has already been set
						// check if we have been bellow the threshold for a while now...
						long interruptEndTime = averageReadings[interruptEndReadingIndex].time;
						if (reading.time - interruptEndTime > 50)
						{
							// we have been bellow the threshold for long enough. This interrupt has ended
							// make sure the interrupt isn't too short
							if (interruptEndTime - interruptStartTime >= MIN_INTERRUPT_DURATION)
							{
								// there was an interrupt!
								++numInterrupts;
								interruptsDisplay.add(new Interrupt(interruptStartTime, interruptEndTime));
							}
							
							// move back to the end of this interrupt so we don't miss any interrupts that started while we were making sure this one ended
							i = interruptEndReadingIndex - 1; // -1 because the for loop will +1
							
							// reset
							insideInterrupt = false;
							interruptStartTime = 0;
							interruptEndReadingIndex = -1;
						}
					}
				}
			}
			
			// look for the start of an interrupt
			else
			{
				// check if we are above the threshold
				if (Math.abs(reading.value) > INTERRUPT_THRESHOLD)
				{
					int tempInterruptSign = reading.value < 0 ? -1 : 1;
					
					// interrupts create a large difference, there should be a substantial slope from a previous point to this one
					// go a couple of readings back
					Reading previousReading = i < 3 ? new Reading(0l, (short)0) : averageReadings[i - 3];
					
					// interrupts create a large difference, there should be a substantial slope between these data points
					// make sure the slope is great enough
					long dtime = (reading.time  - previousReading.time);
					int dvalue = (reading.value - previousReading.value);
					
					if (dtime > 0 && (dvalue / dtime) * tempInterruptSign > MIN_INTERRUPT_START_SLOPE)
					{
						// start interrupt
						insideInterrupt = true;
						interruptStartTime = reading.time;
						interruptSign = tempInterruptSign;
					}
				}
			}
		}
		
		
		int backfillStartingIndex = averageReadings.length;
		
		// check if we stopped in the middle of an interrupt and we were in the middle of making sure an interrupt had ended
		if (insideInterrupt && interruptEndReadingIndex > -1)
		{
			// we must preserve the readings from this interrupt's end onward so after we are done making sure this
			// interrupt ended, we can check for interrupts that started while we were making sure this interrupt ended
			backfillStartingIndex = interruptEndReadingIndex;
		}
		
		int backfillToIndex = backfillStartingIndex - NUM_BACKFILL_AVERAGE_READINGS;
		if (backfillToIndex < 0)
			backfillToIndex = 0;
		
		int numBackfillReadings = averageReadings.length - backfillToIndex;
		
		// create an array for holding the backfill average readings
		backfillAverageReadings = new Reading[numBackfillReadings];
		
		// copy the average readings to the backfill array
		for (int i = 0; i < numBackfillReadings; ++i)
			backfillAverageReadings[i] = averageReadings[backfillToIndex + i]; // make the times relative
		
		if (insideInterrupt)
		{
			// make the end reading index relative
			if (interruptEndReadingIndex > -1)
				interruptEndReadingIndex -= backfillToIndex;
		}
		
		backfillAverageReadingsSkip = backfillStartingIndex - backfillToIndex;
		
		
		displayWidth = averageReadingsDisplay.size() > 0 ? (int)((displayRelativeTime + averageReadingsDisplay.get(averageReadingsDisplay.size() - 1).time) * DATA_X_SCALE) : 0;
		panel.setPreferredSize(new Dimension(displayWidth, SCREEN_HEIGHT - (int)scroller.getHorizontalScrollBar().getPreferredSize().getHeight()));
		panel.revalidate();
		
		scroller.getHorizontalScrollBar().setValue(displayWidth * 100);
		scroller.repaint();
		
		return numInterrupts;
	}
	
	
	// draw graph
	public void drawGraph(Graphics g)
	{
		// interrupts
		for (int i = 0; i < interruptsDisplay.size(); ++i)
		{
			g.setColor(Color.LIGHT_GRAY);
			g.fillRect(
					(int)((displayRelativeTime + interruptsDisplay.get(i).startTime) * DATA_X_SCALE),
					0,
					(int)((interruptsDisplay.get(i).endTime - interruptsDisplay.get(i).startTime) * DATA_X_SCALE),
					SCREEN_HEIGHT);
			
			g.setColor(Color.DARK_GRAY);
			g.drawLine(
					(int)((displayRelativeTime + interruptsDisplay.get(i).startTime) * DATA_X_SCALE),
					0,
					(int)((displayRelativeTime + interruptsDisplay.get(i).startTime) * DATA_X_SCALE),
					SCREEN_HEIGHT);
			
			g.drawLine(
					(int)((displayRelativeTime + interruptsDisplay.get(i).endTime) * DATA_X_SCALE),
					0,
					(int)((displayRelativeTime + interruptsDisplay.get(i).endTime) * DATA_X_SCALE),
					SCREEN_HEIGHT);
		}
		
		
		// guide lines
		g.setColor(Color.GRAY);
		g.drawLine(
				0,
				SCREEN_HEIGHT / 2,
				displayWidth,
				SCREEN_HEIGHT / 2);
		
		g.drawLine(
				0,
				(int)(SCREEN_HEIGHT / 2 + Reading.MAX_VALUE * DATA_Y_SCALE),
				displayWidth,
				(int)(SCREEN_HEIGHT / 2 + Reading.MAX_VALUE * DATA_Y_SCALE));
		
		g.drawLine(
				0,
				(int)(SCREEN_HEIGHT / 2 - Reading.MAX_VALUE * DATA_Y_SCALE),
				displayWidth,
				(int)(SCREEN_HEIGHT / 2 - Reading.MAX_VALUE * DATA_Y_SCALE));
		
		
		// thresholds
		g.setColor(Color.BLUE);
		g.drawLine(
				0,
				(int)(SCREEN_HEIGHT / 2 + INTERRUPT_THRESHOLD * DATA_Y_SCALE),
				displayWidth,
				(int)(SCREEN_HEIGHT / 2 + INTERRUPT_THRESHOLD * DATA_Y_SCALE));
		
		g.drawLine(
				0,
				(int)(SCREEN_HEIGHT / 2 - INTERRUPT_THRESHOLD * DATA_Y_SCALE),
				displayWidth,
				(int)(SCREEN_HEIGHT / 2 - INTERRUPT_THRESHOLD * DATA_Y_SCALE));
		
		
		// raw readings
		/*g.setColor(Color.BLACK);
		for (int i = 1; i < rawReadingsDisplay.size(); ++i)
		{
			g.drawLine(
					(int)((displayRelativeTime + rawReadingsDisplay.get(i - 1).time) * DATA_X_SCALE),
					(int)(SCREEN_HEIGHT / 2 - rawReadingsDisplay.get(i - 1).value * DATA_Y_SCALE),
					(int)((displayRelativeTime + rawReadingsDisplay.get(i).time) * DATA_X_SCALE),
					(int)(SCREEN_HEIGHT / 2 - rawReadingsDisplay.get(i).value * DATA_Y_SCALE));
		}*/
		
		
		// average readings
		
		g.setColor(Color.RED);
		for (int i = 1; i < averageReadingsDisplay.size(); ++i)
		{
			g.drawLine(
					(int)((displayRelativeTime + averageReadingsDisplay.get(i - 1).time) * DATA_X_SCALE),
					(int)(SCREEN_HEIGHT / 2 - averageReadingsDisplay.get(i - 1).value * DATA_Y_SCALE),
					(int)((displayRelativeTime + averageReadingsDisplay.get(i).time) * DATA_X_SCALE),
					(int)(SCREEN_HEIGHT / 2 - averageReadingsDisplay.get(i).value * DATA_Y_SCALE));
		}
		
		/*g.setColor(Color.BLACK);
		for (int i = 0; i < averageReadingsDisplay.size(); ++i)
		{
			g.drawLine(
					(int)((displayRelativeTime + averageReadingsDisplay.get(i).time) * DATA_X_SCALE),
					(int)(SCREEN_HEIGHT / 2 - averageReadingsDisplay.get(i).value * DATA_Y_SCALE) - 5,
					(int)((displayRelativeTime + averageReadingsDisplay.get(i).time) * DATA_X_SCALE),
					(int)(SCREEN_HEIGHT / 2 - averageReadingsDisplay.get(i).value * DATA_Y_SCALE) + 5);
		}*/
		
		
		// scroll position
		g.setColor(Color.BLACK);
		g.drawString("X: " + scrollPosX, scrollPosX + 20, 20);
	}
}


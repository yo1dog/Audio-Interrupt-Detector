package net.devmike.audioInterruptDetector;

import net.devmike.audioInterruptDetector.Interrupt;
import net.devmike.audioInterruptDetector.AudioSample;

public class AudioInterruptDetector
{
	// ===================================================================
	// Constants
	//
	// ===================================================================
	
	// the amplitude a sample must exceed to be considered part of an interrupt
	public static final short INTERRUPT_AMPLITUDE_THRESHOLD = AudioSample.AMPLITUDE_MAX_VALUE / 2; // put the threshold at %50
	
	// number of samples to use to create a normalized sample
	private static final int NUM_SAMPLES_IN_NORMALIZED_GROUP = 10;
	
	// the time to calculate the amplitude delta (change of amplitude) over
	private static final int AMPLITUDE_DELTA_DURATION = 6;
	
	// the minimum amplitude delta required for a sample above the amplitude threshold to be considered the start of an interrupt
	private static final int MIN_AMPLITUDE_DELTA_TO_START_INTERRUPT = AudioSample.AMPLITUDE_MAX_VALUE / 4; // must rise/fall %25
	
	private static final int MIN_INTERRUPT_DURATION = 20;   // min duration of a valid interrupt
	private static final int MAX_INTERRUPT_DURATION = 4000; // max duration of a valid interrupt
	
	// time the amplitude must remain under the threshold for an interrupt to end 
	private static final int DURATION_UNDER_THRESHOLD_TO_END_INTERRUPT = 10;
	
	// the number of normalized samples to keep after processAudioData to use at the beginning of the next processAudioData
	// this is used for interrupt checks and operations that require looking back in time
	private static final int NUM_NORMALIZED_SAMPLES_TO_BACKFILL_FOR_CHECKS = 10;
	
	
	
	// ===================================================================
	// Variables
	//
	// ===================================================================
	
	// current relative time unit
	// we assume a sample rate of 44100Hz, therefore one time unit is 1/44100 seconds
	private long time = AudioSample.TIME_MIN_VALUE;
	
	// visualizer to add samples and interrupts to
	private AudioInterruptVisualizer visualizer;
	
	
	// -------------------------------------------------------------------
	// intermediate processAudioData variables
	
	// if we are given an odd number of bytes to read, remove the last one and use it at the start of the next processAudioData
	private byte    leftoverAudioDataByte;
	private boolean useLeftoverAudioDataByte = false;
	
	// raw samples that did not get used to create a normalized sample to be used at the start of the next processAudioData
	private AudioSample[] leftoverRawSamples = new AudioSample[0];
	
	// normalized samples back-filled for look-back checks and operations to be used at the start of the next processAudioData
	private AudioSample[] backfilledNormalizedSamples = new AudioSample[0];
	
	// sign of the last interrupt
	private int lastInterruptAmplitudeSign = 0;
	
	// interrupt detection status
	private boolean insidePossibleInterrupt                           = false; // if we are inside a potential interrupt
	private long    possibleInterruptStartTime                        = 0;     // the start time of the potential interrupt
	private int     possibleInterruptPossibleEndNormalizedSampleIndex = -1;    // the potential end normalized sample index of the potential interrupt
	private int     possibleInterruptAmplitudeSign                    = 0;     // sign of the potential interrupt's start amplitude (1 or -1). Defines what side the "wave" is on
	
	
	
	// ===================================================================
	// Methods
	//
	// ===================================================================
	
	public AudioInterruptDetector()
	{
		this(null);
	}
	public AudioInterruptDetector(AudioInterruptVisualizer visualizer)
	{
		this.visualizer = visualizer;
	}
	
	/**
	 * @see #processAudioData(byte[], int, int, boolean)
	 */
	public int processAudioData(byte[] data, int offset, int dataLength)
	{
		return processAudioData(data, offset, dataLength, true);
	}
	
	/**
	 * Processes the given audio data to find interrupts.<br />
	 * <br />
	 * Audio is assumed to have a sample rate of 44100Hz and sample size of 16 bits (2 bytes).
	 * <br />
	 * Data passed to this method over multiple executions is treated as one data set. Meaning, data from
	 * previous executions of this function may be used during subsequent executions. This is intended for
	 * uses such as streaming where small data sets can be passed to this function without having to worry
	 * about interrupts being cut off or not detected. Only necessary data is kept in memory so excessive
	 * memory build up will not occur with multiple executions of this function.
	 * 
	 * @param data       - Audio data.
	 * @param dataOffset - Offset to start from in bytes.
	 * @param dataLength - Number of bytes to read.
	 * @param bigEndian  - If the data is big-endian (true) or little-endian (false).
	 * 
	 * @return The number of interrupts detected.
	 */
	public int processAudioData(
			byte[] data, int dataOffset, int dataLength, boolean bigEndian)
	{
		// number of detected interrupts
		int numInterrupts = 0;
		
		
		// because we need two bytes for every sample, the data length must be kept even
		int evenDataLength = dataLength;
		
		if (useLeftoverAudioDataByte) // account for the offset of using the leftover byte
			++evenDataLength;
		
		if (evenDataLength % 2 == 1)
			--evenDataLength;
		
		
		// calculate ahead how many raw and normalized samples will be created and used this iteration
		int numRawSamplesWillBeCreated        = evenDataLength / 2;                                           // number of raw samples that will be created
		int totalNumRawSamples                = leftoverRawSamples.length + numRawSamplesWillBeCreated;       // total number of raw samples (leftover and will be created)
		int numNormalizedSamplesWillBeCreated = totalNumRawSamples / NUM_SAMPLES_IN_NORMALIZED_GROUP;         // number of normalized samples that will be created (rounded down)
		int numRawSamplesWillBeUsed           = numNormalizedSamplesWillBeCreated * NUM_SAMPLES_IN_NORMALIZED_GROUP; // number of raw samples that will be used
		
		
		// -------------------------------------------------------------------
		// raw samples
		
		// create an array for the raw samples
		int numRawSamples = 0;
		AudioSample[] rawSamples = new AudioSample[numRawSamplesWillBeUsed]; // only need to store the raw samples we will use this iteration
		
		// create an array for the leftover raw samples
		int numLeftoverRawSamples = 0;
		AudioSample[] leftoverRawSamplesNew = new AudioSample[totalNumRawSamples - numRawSamplesWillBeUsed];
		
		
		// if we wont be using any raw samples...
		if (numRawSamplesWillBeUsed == 0)
		{
			// copy the old leftovers to the new leftovers
			for (int i = 0; i < leftoverRawSamples.length; ++i)
				leftoverRawSamplesNew[i] = leftoverRawSamples[i];
			
			numLeftoverRawSamples = leftoverRawSamples.length;
		}
		else
		{
			// copy the leftovers to the array to be used
			for (int i = 0; i < leftoverRawSamples.length; ++i)
				rawSamples[i] = leftoverRawSamples[i];
			
			numRawSamples = leftoverRawSamples.length;
		}
		
		leftoverRawSamples = leftoverRawSamplesNew;
		leftoverRawSamplesNew = null;
		
		
		
		// iterate through the data bytes and create the raw samples
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
			
			
			// create the raw audio sample
			AudioSample rawSample = new AudioSample(time++, dataVal); // we assume 1 unit of time between each sample, so we just increment time
			
			// check if we have already created all the raw samples we are going to use
			if (numRawSamples < numRawSamplesWillBeUsed)
				rawSamples[numRawSamples++] = rawSample; // put in use array
			else
				leftoverRawSamples[numLeftoverRawSamples++] = rawSample; // put in leftover array
				
			if (visualizer != null)
				visualizer.addRawSample(rawSample);
		}
		
		
		
		// -------------------------------------------------------------------
		// normalized samples
		
		// create an array for holding the normalized samples
		int numNormalizedSamples = 0;
		AudioSample[] normalizedSamples = new AudioSample[backfilledNormalizedSamples.length + numNormalizedSamplesWillBeCreated];
		
		// add the back-filled normalized samples
		for (int i = 0; i < backfilledNormalizedSamples.length; ++i)
			normalizedSamples[i] = backfilledNormalizedSamples[i];
		
		numNormalizedSamples = backfilledNormalizedSamples.length;
		
		
		// create the normalized samples from the raw samples
		for (int i = 0; i < numNormalizedSamplesWillBeCreated; ++i)
		{
			// total the amplitudes
			int totalAmplitude = 0;
			
			for (int j = 0; j < NUM_SAMPLES_IN_NORMALIZED_GROUP; ++j)
				totalAmplitude += rawSamples[i * NUM_SAMPLES_IN_NORMALIZED_GROUP + j].amplitude;
			
			// create the normalized sample
			AudioSample normalizedSample = new AudioSample(
					rawSamples[i * NUM_SAMPLES_IN_NORMALIZED_GROUP].time,       // use the time of the first raw sample
					(short)(totalAmplitude / NUM_SAMPLES_IN_NORMALIZED_GROUP)); // average the amplitudes
			
			// put in the array
			normalizedSamples[numNormalizedSamples++] = normalizedSample;
			
			if (visualizer != null)
				visualizer.addNormalizedSample(normalizedSample);
		}
		
		
		
		// -------------------------------------------------------------------
		// look for interrupts
		
		// skip the back-filled samples
		for (int i = backfilledNormalizedSamples.length; i < normalizedSamples.length; ++i)
		{
			AudioSample normalizedSample = normalizedSamples[i];
			
			// check if we are already inside a possible interrupt
			if (insidePossibleInterrupt)
			{
				// check if we are below the interrupt amplitude threshold. Using possibleInterruptSign instead of Math.abs accounts for dramatic shifts from one sign to the other 
				if (normalizedSample.amplitude * possibleInterruptAmplitudeSign > INTERRUPT_AMPLITUDE_THRESHOLD)
				{
					// we are above the threshold, the interrupt is not ending
					possibleInterruptPossibleEndNormalizedSampleIndex = -1;
					
					// don't go over the max interrupt length
					if (normalizedSample.time - possibleInterruptStartTime > MAX_INTERRUPT_DURATION)
					{
						// reset
						insidePossibleInterrupt = false;
						possibleInterruptStartTime = 0;
						possibleInterruptPossibleEndNormalizedSampleIndex = -1;
					}
				}
				else
				{
					// we are below the threshold, the interrupt is POSSIBLY ending
					// set the possible end if it has not been set yet
					if (possibleInterruptPossibleEndNormalizedSampleIndex == -1)
						possibleInterruptPossibleEndNormalizedSampleIndex = i;
					else
					{
						// the possible end has already been set
						long possibleInterruptEndTime = normalizedSamples[possibleInterruptPossibleEndNormalizedSampleIndex].time;
						
						// check if we have been bellow the threshold for a while now...
						if (normalizedSample.time - possibleInterruptEndTime > DURATION_UNDER_THRESHOLD_TO_END_INTERRUPT)
						{
							// we have been bellow the threshold for long enough. This interrupt has ended
							int  interruptEndNormalizedSampleIndex = possibleInterruptPossibleEndNormalizedSampleIndex;
							int  interruptAmplitudeSign            = possibleInterruptAmplitudeSign;
							
							// make sure the interrupt isn't too short
							if (possibleInterruptEndTime - possibleInterruptStartTime >= MIN_INTERRUPT_DURATION)
							{
								// there was an interrupt!
								++numInterrupts;
								
								// create the interrupt
								Interrupt interrupt = new Interrupt(possibleInterruptStartTime, possibleInterruptEndTime);
								
								if (visualizer != null)
									visualizer.addInterrupt(interrupt);
								
								// set the last interrupt sign
								lastInterruptAmplitudeSign = interruptAmplitudeSign;
							}
							
							// move back to the end of this interrupt so we don't miss any interrupts that started while we were making sure this one ended
							i = interruptEndNormalizedSampleIndex - 1; // -1 because the for loop will +1
							
							// reset
							insidePossibleInterrupt = false;
							possibleInterruptStartTime = 0;
							possibleInterruptPossibleEndNormalizedSampleIndex = -1;
						}
					}
				}
			}
			
			
			// we are not already inside a possible interrupt
			// look for the start of an interrupt
			else
			{
				// check if we are above the threshold
				if (Math.abs(normalizedSample.amplitude) > INTERRUPT_AMPLITUDE_THRESHOLD)
				{
					// get the sign of the amplitude
					int tempPossibleInterruptAmplitudeSign = normalizedSample.amplitude < 0 ? -1 : 1;
					
					// make sure we are on the opposite side of the last interrupt
					if (tempPossibleInterruptAmplitudeSign != lastInterruptAmplitudeSign)
					{
						// interrupts create a large difference in the amplitude, there should be a substantial change in amplitude (delta) from a previous sample to this one
						// go a couple of samples back
						AudioSample previousNormalizedSample = i < AMPLITUDE_DELTA_DURATION ? new AudioSample(0l, (short)0) : normalizedSamples[i - AMPLITUDE_DELTA_DURATION];
						
						// make sure the amplitude delta is great enough
						int dAmplitude = (normalizedSample.amplitude - previousNormalizedSample.amplitude);
						
						
						// using the sign instead of Math.abs keeps deltas in the wrong direction from passing
						if (dAmplitude * tempPossibleInterruptAmplitudeSign > MIN_AMPLITUDE_DELTA_TO_START_INTERRUPT)
						{
							// start interrupt
							insidePossibleInterrupt        = true;
							possibleInterruptStartTime     = normalizedSample.time;
							possibleInterruptAmplitudeSign = tempPossibleInterruptAmplitudeSign;
						}
					}
				}
			}
		}
		
		
		
		// -------------------------------------------------------------------
		// set intermediate variables
		
		// get the index of the normalized sample to back-fill to
		int backfillToIndex = normalizedSamples.length;
		
		// check if we stopped in the middle of a possible interrupt and we were in the middle of making sure it had ended
		if (insidePossibleInterrupt && possibleInterruptPossibleEndNormalizedSampleIndex > -1)
		{
			// we must preserve the samples from this interrupt's end onward so after we are done making sure this
			// interrupt ended, we can check for interrupts that started while we were making sure this interrupt ended
			backfillToIndex = possibleInterruptPossibleEndNormalizedSampleIndex; // back-fill to the interrupt's end
		}
		
		// back-fill additional samples for the look-back interrupt checks
		backfillToIndex -= NUM_NORMALIZED_SAMPLES_TO_BACKFILL_FOR_CHECKS;
		
		if (backfillToIndex < 0)
			backfillToIndex = 0;
		
		int numBackfilledNormalizedSamples = normalizedSamples.length - backfillToIndex;
		
		// create an array for holding the back-filled normalized samples
		backfilledNormalizedSamples = new AudioSample[numBackfilledNormalizedSamples];
		
		// copy to the back-fill array
		for (int i = 0; i < numBackfilledNormalizedSamples; ++i)
			backfilledNormalizedSamples[i] = normalizedSamples[backfillToIndex + i];
		
		// make the interrupt end normalized sample index relative
		if (insidePossibleInterrupt && possibleInterruptPossibleEndNormalizedSampleIndex > -1)
			possibleInterruptPossibleEndNormalizedSampleIndex -= backfillToIndex;
		
		
		// refresh the visualizer
		if (visualizer != null)
			visualizer.refresh();
		
		// done!
		return numInterrupts;
	}
}

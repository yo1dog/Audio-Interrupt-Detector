package net.devmike.audioInterruptDetector;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import net.devmike.audioInterruptDetector.AudioInterruptVisualizer;

public class Main
{
	public static void main(String[] args) throws Exception
	{
		// create the visualizer
		AudioInterruptVisualizer audioVisualizer = new AudioInterruptVisualizer(AudioInterruptDetector.INTERRUPT_AMPLITUDE_THRESHOLD);
		
		// create the detector
		AudioInterruptDetector audioInterruptDetector = new AudioInterruptDetector(audioVisualizer);
		
		
		//streamFromFile(audioInterruptDetector);
		streamFromMic(audioInterruptDetector);
	}
	
	private static void streamFromMic(AudioInterruptDetector audioInterruptDetector) throws Exception
	{
		// create our format
		AudioFormat audioFormat = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED, // encoding
				88200.0f,                        // sample rate. NOTE: Changing this may throw off several algorithms since we assume 1 unit of time between each sample
				16,                              // sample size in bits. NOTE: If you change this, you will have to change the amplitude data type.
				1,                               // channels
				2,                               // frame size
				88200.0f,                        // frame rate
				true);                           // big-endian
		
		
		// create the data line info
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
		if (!AudioSystem.isLineSupported(info))
			throw new Exception("Line is not supported");
		
		// get the data line
		TargetDataLine line = (TargetDataLine)AudioSystem.getLine(info);
		line.open(audioFormat);
		
		// start listening
		line.start();
		
		long numIterrupts = 0;
		byte[] audioByteBuffer = new byte[line.getBufferSize()];
		while (true)
		{
			// read bytes from the line
			int numBytesRead = line.read(audioByteBuffer, 0, 180);
			
			if (line.available() > line.getBufferSize() / 2)
				System.err.println("Getting behind! " + line.available());
			
			if (numBytesRead > -1)
				numIterrupts += audioInterruptDetector.processAudioData(audioByteBuffer, 0, numBytesRead, audioFormat.isBigEndian());
			
			Thread.sleep(1);
		}
	}
	
	private static void streamFromFile(AudioInterruptDetector audioInterruptDetector) throws Exception
	{
		// get test file
		File testFile = new File(System.getProperty("user.dir") + File.separatorChar + "res" + File.separatorChar + "testFlowMeterVeryFast.wav");
		
		// load file as audio stream
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(testFile);
		
		// get the audio format from the steam
		AudioFormat audioFormat = audioInputStream.getFormat();
		
		// get additional info from the audio format
		DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
		
		// get the data line from the info
		SourceDataLine sourceDataLine = (SourceDataLine)AudioSystem.getLine(dataLineInfo);
		sourceDataLine.open(audioFormat);
		
		// open the audio stream into the clip
		int numBytesRead = 0;
		int totalNumBytesRead = 0;
		byte[] audioByteBuffer = new byte[16];
		
		sourceDataLine.start();
		
		int numIterrupts = 0;
		
		do
		{
			// read the bytes into the buffer
			try
			{
				numBytesRead = audioInputStream.read(audioByteBuffer, 0, audioByteBuffer.length);
			}
			catch (IOException e)
			{
				e.printStackTrace();
				continue;
			}
			
			// process the data we have read so far
			if (numBytesRead > 0)
			{
				numIterrupts += audioInterruptDetector.processAudioData(audioByteBuffer, 0, numBytesRead, audioFormat.isBigEndian());
				totalNumBytesRead += numBytesRead;
			}
			
			Thread.sleep(1);
		}
		while (numBytesRead > -1);
		
		sourceDataLine.close();
		
		System.out.println("Done Reading");
		System.out.println("Read " + totalNumBytesRead + " bytes total");
		System.out.println(numIterrupts + " Iterrupts");
	}
}

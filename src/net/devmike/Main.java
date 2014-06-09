package net.devmike;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class Main
{
	public static void main(String[] args) throws Exception
	{
		AudioVisualizer audioVisualizer = new AudioVisualizer();
		//streamFromFile(audioVisualizer);
		streamFromMic(audioVisualizer);
	}
	
	private static void streamFromMic(AudioVisualizer audioVisualizer) throws Exception
	{
		// create our format
		AudioFormat audioFormat = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED, // encoding
				44100.0f,                        // sample rate
				16,                              // sample size in bits
				1,                               // channels
				2,                               // frame size
				44100.0f,                        // frame rate
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
		
		int numIterrupts = 0;
		
		byte[] audioByteBuffer = new byte[line.getBufferSize()];
		while (true)
		{
			// read bytes from the line
			int numBytesRead = line.read(audioByteBuffer, 0, audioByteBuffer.length);
			
			// process the data we read
			if (numBytesRead > 0)
				numIterrupts += audioVisualizer.processAudioData(audioByteBuffer, 0, numBytesRead, audioFormat.isBigEndian());
			
			System.out.println(numIterrupts);
		}
	}
	
	private static void streamFromFile(AudioVisualizer audioVisualizer) throws Exception
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
				numIterrupts += audioVisualizer.processAudioData(audioByteBuffer, 0, numBytesRead, audioFormat.isBigEndian());
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

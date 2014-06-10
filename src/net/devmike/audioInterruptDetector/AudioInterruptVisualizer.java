package net.devmike.audioInterruptDetector;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;


public class AudioInterruptVisualizer
{
	// ===================================================================
	// Constants
	//
	// ===================================================================
	
	// screen size
	// TODO: make dynamic
	private static final int SCREEN_WIDTH  = 1200;
	private static final int SCREEN_HEIGHT = 500;
	
	// min/max Y value of the visualization 
	private static int VISUALIZATION_Y_RANGE = SCREEN_HEIGHT / 2 - 50; // use the full screen height with some padding
	
	// relative Y position for drawing the visualization
	private static int VISUALIZATION_Y_ORIGIN = SCREEN_HEIGHT / 2; // use the middle of the screen as the Y origin
	
	// visualization drawing scales
	private static double VISUALIZATION_X_SCALE = 0.01d;
	private static double VISUALIZATION_Y_SCALE = (double)VISUALIZATION_Y_RANGE / AudioSample.AMPLITUDE_MAX_VALUE; // scale so amplitude values will fit in the Y range
	
	// the amount of time samples and interrupts should be stored for
	private static int DATA_STORE_DURATION = (int)(SCREEN_WIDTH / VISUALIZATION_X_SCALE);
	
	// width of the visualization rounded up
	private static int VISUALIZATION_WIDTH = (int)(DATA_STORE_DURATION * VISUALIZATION_X_SCALE + 0.5d);
	
	// threshold used to calculate interrupts
	private final short interruptAmplitudeThreshold;
	
	
	
	// ===================================================================
	// Variables
	//
	// ===================================================================
	
	// -------------------------------------------------------------------
	// window elements
	
	private AudioVisualizerPanel panel;
	private JScrollPane          scroller;
	
	private int scrollPosX = 0; // TODO: get scroll position directly from scroll pane or scroll bar
	
	// keep track of some stats
	private long totalNumInterrupts = 0;
	private long totalNumRawSamples = 0;
	
	
	// -------------------------------------------------------------------
	// visualization data
	
	private ArrayList<AudioSample> rawSamples        = new ArrayList<AudioSample>(); // raw audio samples
	private ArrayList<AudioSample> normalizedSamples = new ArrayList<AudioSample>(); // normalized audio samples
	private ArrayList<Interrupt>   interrupts        = new ArrayList<Interrupt>();   // detected interrupts
	
	// relative time for drawing the visualization
	private long visualizationTimeOffset = AudioSample.TIME_MAX_VALUE;
	
	
	
	// ===================================================================
	// Private Classes
	//
	// ===================================================================
	
	/**
	 * Custom panel for drawing our audio visualization.
	 */
	private class AudioVisualizerPanel extends JPanel
	{
		private static final long serialVersionUID = 1l;
		
		public void paint(Graphics g)
		{
			drawVisualization(g);
		}
	}
	
	
	
	// ===================================================================
	// Methods
	//
	// ===================================================================
	
	/**
	 * Creates a window for visualizing audio samples and detected interrupts.
	 * 
	 * @param interruptAmplitudeThreshold - threshold used to calculate interrupts
	 */
	public AudioInterruptVisualizer(short interruptAmplitudeThreshold)
	{
		this.interruptAmplitudeThreshold = interruptAmplitudeThreshold;
		
		// I know very little about Java windows, so the bellow code is probably terrible...
		// please let me know how I can improve it.
		
		// create the frame
		final JFrame frame = new JFrame("AudioVisualizer");
		
		frame.setBackground(Color.BLACK);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		frame.addComponentListener(new ComponentListener()
		{
			public void componentResized(ComponentEvent e)
			{
				// TODO: 
				VISUALIZATION_Y_RANGE = frame.getHeight() / 2 - 50; // use the full screen height with some padding
				
				// relative Y position for drawing the visualization
				VISUALIZATION_Y_ORIGIN = frame.getHeight() / 2; // use the middle of the screen as the Y origin
				
				// visualization drawing scales
				VISUALIZATION_X_SCALE = 0.01d;
				VISUALIZATION_Y_SCALE = (double)VISUALIZATION_Y_RANGE / AudioSample.AMPLITUDE_MAX_VALUE; // scale so amplitude values will fit in the Y range
			}

			public void componentHidden(ComponentEvent e) {}
			public void componentMoved(ComponentEvent e) {}
			public void componentShown(ComponentEvent e) {}
		});
		
		// create the panel
		panel = new AudioVisualizerPanel();
		panel.setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
		frame.getContentPane().add(panel, java.awt.BorderLayout.CENTER);
		
		// create the scroll pane
		scroller = new JScrollPane(panel);
		scroller.getHorizontalScrollBar().addAdjustmentListener(new AdjustmentListener()
		{
			// on scroll, record the x position and repaint
			public void adjustmentValueChanged(AdjustmentEvent e)
			{
				scrollPosX = e.getValue();
				scroller.repaint();
			}
		});
		frame.getContentPane().add(scroller, java.awt.BorderLayout.CENTER);
		
		// finalize and display
		frame.pack();
		frame.setVisible(true);
		
		// set the preferred size to the max possible visualization size
		panel.setPreferredSize(new Dimension(
				VISUALIZATION_WIDTH,
				SCREEN_HEIGHT - (int)scroller.getHorizontalScrollBar().getPreferredSize().getHeight()));
		panel.revalidate();
	}
	
	
	
	/**
	 * Adds a sample to the list of raw samples to be displayed.<br />
	 * <br />
	 * Make sure to call {@link #refresh} after adding samples.
	 * @see #refresh
	 * 
	 * @param sample - Raw sample to add.
	 */
	public void addRawSample(AudioSample sample)
	{
		rawSamples.add(sample);
		++totalNumRawSamples;
	}
	
	/**
	 * Adds a sample to the list of normalized samples to be displayed.<br />
	 * <br />
	 * Make sure to call {@link #refresh} after adding samples.
	 * @see #refresh
	 * 
	 * @param sample - Normalized sample to add.
	 */
	public void addNormalizedSample(AudioSample sample)
	{
		normalizedSamples.add(sample);
	}
	
	/**
	 * Adds an interrupt to the list of interrupts to be displayed.
	 * 
	 * @param interrupt - Interrupt to add.
	 */
	public void addInterrupt(Interrupt interrupt)
	{
		interrupts.add(interrupt);
		++totalNumInterrupts;
	}
	
	
	/**
	 * Resets the display and removes old data.<br />
	 * <br />
	 * This should be called after one or more samples are added via {@link #addRawSample} or {@link #addNormalizedSample}.
	 */
	public void refresh()
	{
		// remove old samples
		removeOldSamples(rawSamples,        DATA_STORE_DURATION);
		removeOldSamples(normalizedSamples, DATA_STORE_DURATION);
		
		// the oldest time comes from the oldest sample
		long oldestTime = rawSamples.get(0).time;
		
		// remove old interrupts
		removeOldInterrupts(interrupts, oldestTime);
		
		// set the time offset so the oldest time will be displayed at X=0
		visualizationTimeOffset = -oldestTime;
		
		// redraw
		scroller.repaint();
	}
	
	
	/**
	 * Removes all samples from the list that are too old
	 * 
	 * @param samples - List to remove samples from.
	 * @param minTime - If samples are older than this time, they will be removed.
	 */
	private void removeOldSamples(ArrayList<AudioSample> samples, int storeDuration)
	{
		// because we assume 1 unit of time between samples, we can use the indexes as relative time
		int numSamplesToRemove = samples.size() - storeDuration;
		
		// make sure we have something to remove
		if (numSamplesToRemove <= 0)
			return;
		
		// remove the old samples
		samples.subList(0, numSamplesToRemove).clear();
	}
	
	/**
	 * Removes all interrupts from the list whose end time is older than the given time.
	 * 
	 * @param interrupts - List to remove interrupts from.
	 * @param oldestTime - If interrupts are older than this time, they will be removed.
	 */
	private void removeOldInterrupts(ArrayList<Interrupt> interrupts, long oldestTime)
	{
		int firstInterruptToSaveIndex = -1;
		for (int i = 0; i < interrupts.size(); ++i)
		{
			Interrupt interrupt = interrupts.get(i);
			
			// find the first interrupt that is not too old
			if (interrupt.endTime >= oldestTime)
			{
				firstInterruptToSaveIndex = i;
				break;
			}
		}
		
		// if we found an interrupt that is not too old...
		if (firstInterruptToSaveIndex > -1)
		{
			// remove the old interrupts
			interrupts.subList(0, firstInterruptToSaveIndex).clear();
		}
		else
		{
			// they are all too old, remove them all
			interrupts.clear();
		}
	}
	
	
	
	
	/**
	 * Draws the visualization.
	 * 
	 * @param g - Graphics to draw with.
	 */
	public void drawVisualization(Graphics g)
	{
		// -------------------------------------------------------------------
		// interrupts
		
		for (int i = 0; i < interrupts.size(); ++i)
		{
			Interrupt interrupt = interrupts.get(i);
			
			// draw a box from the interrupt's start time to end time
			g.setColor(Color.LIGHT_GRAY);
			g.fillRect(
					getXForTime(interrupt.startTime),
					0,
					(int)((interrupt.endTime - interrupt.startTime) * VISUALIZATION_X_SCALE),
					SCREEN_HEIGHT);
			
			// draw a line on the interrupt's start time
			g.setColor(Color.DARK_GRAY);
			g.drawLine(
					getXForTime(interrupt.startTime), 0,
					getXForTime(interrupt.startTime), SCREEN_HEIGHT);
			
			// draw a line on the interrupt's end time
			g.drawLine(
					getXForTime(interrupt.endTime), 0,
					getXForTime(interrupt.endTime), SCREEN_HEIGHT);
		}
		
		
		
		// -------------------------------------------------------------------
		// guide lines
		
		// draw a line at the Y origin
		g.setColor(Color.GRAY);
		g.drawLine(
				0,                   VISUALIZATION_Y_ORIGIN,
				VISUALIZATION_WIDTH, VISUALIZATION_Y_ORIGIN);
		
		// draw a line at the max Y
		g.drawLine(
				0,                   getYForAmplitude(AudioSample.AMPLITUDE_MAX_VALUE),
				VISUALIZATION_WIDTH, getYForAmplitude(AudioSample.AMPLITUDE_MAX_VALUE));
		
		// draw a line at the min Y
		g.drawLine(
				0,                   getYForAmplitude(AudioSample.AMPLITUDE_MIN_VALUE),
				VISUALIZATION_WIDTH, getYForAmplitude(AudioSample.AMPLITUDE_MIN_VALUE));
		
		
		
		// -------------------------------------------------------------------
		// thresholds
		
		// draw a line at the top threshold
		g.setColor(Color.BLUE);
		g.drawLine(
				0,                   getYForAmplitude(interruptAmplitudeThreshold),
				VISUALIZATION_WIDTH, getYForAmplitude(interruptAmplitudeThreshold));
		
		g.drawLine(
				0,                   getYForAmplitude((short)-interruptAmplitudeThreshold),
				VISUALIZATION_WIDTH, getYForAmplitude((short)-interruptAmplitudeThreshold));
		
		
		
		// -------------------------------------------------------------------
		// raw samples
		
		/*g.setColor(Color.BLACK);
		for (int i = 1; i < rawSamples.size(); ++i)
		{
			AudioSample sample         = rawSamples.get(i);
			AudioSample previousSample = rawSamples.get(i - 1);
			
			// draw a line from the previous sample to this one
			g.drawLine(
					getXForTime(previousSample.time), getYForAmplitude(previousSample.amplitude),
					getXForTime(sample        .time), getYForAmplitude(sample        .amplitude));
		}*/
		
		
		
		// -------------------------------------------------------------------
		// normalized samples
		
		g.setColor(Color.RED);
		for (int i = 1; i < normalizedSamples.size(); ++i)
		{
			AudioSample sample         = normalizedSamples.get(i);
			AudioSample previousSample = normalizedSamples.get(i - 1);
			
			// draw a line from the previous sample to this one
			g.drawLine(
					getXForTime(previousSample.time), getYForAmplitude(previousSample.amplitude),
					getXForTime(sample        .time), getYForAmplitude(sample        .amplitude));
		}
		
		// draw tick marks on each normalized sample
		/*g.setColor(Color.BLACK);
		for (int i = 0; i < normalizedSamples.size(); ++i)
		{
			AudioSample sample = normalizedSamples.get(i);
			
			g.drawLine(
					getXForTime(sample.time), getYForAmplitude(sample.amplitude) - 5,
					getXForTime(sample.time), getYForAmplitude(sample.amplitude) + 5);
		}*/
		
		
		
		// -------------------------------------------------------------------
		// info
		
		// draw the scroll position
		g.setColor(Color.BLACK);
		g.drawString("X: "                 + scrollPosX,         scrollPosX + 20, 15);
		g.drawString("Interrupts: "        + totalNumInterrupts, scrollPosX + 20, 30);
		g.drawString("Samples Processed: " + totalNumRawSamples, scrollPosX + 20, 45);
	}
	
	/**
	 * Translates the given time into an X position on the visualization using the
	 * current time offset and X scale.
	 * 
	 * @param time - Time to translate.
	 * 
	 * @return X position on the visualization.
	 */
	private int getXForTime(long time)
	{
		return (int)((visualizationTimeOffset + time) * VISUALIZATION_X_SCALE);
	}
	
	/**
	 * Translates the given amplitude into an Y position on the visualization using the
	 * Y origin and Y scale.
	 * 
	 * @param amplitude - Amplitude to translate.
	 * 
	 * @return Y position on the visualization.
	 */
	private int getYForAmplitude(short amplitude)
	{
		return (int)(VISUALIZATION_Y_ORIGIN + amplitude * VISUALIZATION_Y_SCALE);
	}
}


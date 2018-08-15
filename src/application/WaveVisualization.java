/*
 * 
 */
package application;

import application.WaveFormService.WaveFormJob;
import javafx.animation.AnimationTimer;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * The Class Visualizer.
 *
 * @author GOXR3PLUS
 */
public class WaveVisualization extends WaveFormPane {
	
	/*** This Service is constantly repainting the wave */
	private final PaintService animationService;
	
	/*** This Service is creating the wave data for the painter */
	private final WaveFormService waveService;
	
	private boolean recalculateWaveData;
	
	/**
	 * Constructor
	 * 
	 * @param width
	 * @param height
	 */
	public WaveVisualization(int width, int height) {
		super(width, height);
		super.setWaveVisualization(this);
		waveService = new WaveFormService(this);
		animationService = new PaintService();
		
		// ----------
		widthProperty().addListener((observable , oldValue , newValue) -> {
			//System.out.println("New Visualizer Width is:" + newValue);
			
			// Canvas Width
			this.width = Math.round(newValue.floatValue());
			
			//Draw single line :)
			recalculateWaveData = true;
			clear();
			
		});
		// -------------
		heightProperty().addListener((observable , oldValue , newValue) -> {
			//System.out.println("New Visualizer Height is:" + newValue);
			
			// Canvas Height
			this.height = Math.round(newValue.floatValue());
			
			//Draw single line :)
			recalculateWaveData = true;
			clear();
		});
		
		//Tricky mouse events
		setOnMouseMoved(m -> this.setMouseXPosition((int) m.getX()));
		setOnMouseExited(m -> this.setMouseXPosition(-1));
		
	}
	//--------------------------------------------------------------------------------------//
	
	/**
	 * @return the animationService
	 */
	public PaintService getAnimationService() {
		return animationService;
	}
	
	public WaveFormService getWaveService() {
		return waveService;
	}
	
	//--------------------------------------------------------------------------------------//
	
	/**
	 * Stars the wave visualiser painter
	 */
	public void startPainterService() {
		animationService.start();
	}
	
	/**
	 * Stops the wave visualiser painter
	 */
	public void stopPainterService() {
		animationService.stop();
		clear();
	}
	
	/**
	 * @return True if AnimationTimer of Visualiser is Running
	 */
	public boolean isPainterServiceRunning() {
		return animationService.isRunning();
	}
	
	/*-----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * 
	 * 							      Paint Service
	 * 
	 * -----------------------------------------------------------------------
	 * 
	 * -----------------------------------------------------------------------
	 */
	/**
	 * This Service is updating the visualizer.
	 *
	 * @author GOXR3PLUS
	 */
	public class PaintService extends AnimationTimer {
		
		/*** When this property is <b>true</b> the AnimationTimer is running */
		private volatile SimpleBooleanProperty running = new SimpleBooleanProperty(false);
		
		private long previousNanos = 0;
		
		@Override
		public void start() {
			// Values must be >0
			if (width <= 0 || height <= 0)
				width = height = 1;
			
			super.start();
			running.set(true);
		}
		
		public WaveVisualization getWaveVisualization() {
			return WaveVisualization.this;
		}
		
		@Override
		public void handle(long nanos) {
			//System.out.println("Running...")
			
			//Every 300 millis update
			if (nanos >= previousNanos + 100000 * 1000) { //
				previousNanos = nanos;
				setTimerXPosition(getTimerXPosition() + 1);
			}
			
			//If resulting wave is not calculated
			if (getWaveService().getResultingWaveform() == null || recalculateWaveData) {
				
				//Start the Service
				getWaveService().startService(getWaveService().getFileAbsolutePath(), WaveFormJob.WAVEFORM);
				recalculateWaveData = false;
				
				return;
			}
			
			//Paint			
			paintWaveForm();
		}
		
		@Override
		public void stop() {
			super.stop();
			running.set(false);
		}
		
		/**
		 * @return True if AnimationTimer is running
		 */
		public boolean isRunning() {
			return running.get();
		}
		
		/**
		 * @return Running Property
		 */
		public SimpleBooleanProperty runningProperty() {
			return running;
		}
		
	}
	
}

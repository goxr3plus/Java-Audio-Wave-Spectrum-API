package soundcloud;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.apache.commons.io.FilenameUtils;

import ws.schild.jave.AudioAttributes;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.EncodingAttributes;
import ws.schild.jave.MultimediaObject;

public class BoxWaveform {
	static int boxWidth = 4;
	static Dimension size = new Dimension(boxWidth == 1 ? 512 : 513, 97);
	
	static BufferedImage img;
	static JPanel view;
	
	// draw the image
	static void drawImage(float[] samples) {
		Graphics2D g2d = img.createGraphics();
		
		int numSubsets = size.width / boxWidth;
		int subsetLength = samples.length / numSubsets;
		
		float[] subsets = new float[numSubsets];
		
		// find average(abs) of each box subset
		int s = 0;
		for (int i = 0; i < subsets.length; i++) {
			
			double sum = 0;
			for (int k = 0; k < subsetLength; k++) {
				sum += Math.abs(samples[s++]);
			}
			
			subsets[i] = (float) ( sum / subsetLength );
		}
		
		// find the peak so the waveform can be normalized
		// to the height of the image
		float normal = 0;
		for (float sample : subsets) {
			if (sample > normal)
				normal = sample;
		}
		
		// normalize and scale
		normal = 32768.0f / normal;
		for (int i = 0; i < subsets.length; i++) {
			subsets[i] *= normal;
			subsets[i] = ( subsets[i] / 32768.0f ) * ( size.height / 2 );
		}
		
		g2d.setColor(Color.ORANGE);
		
		// convert to image coords and do actual drawing
		for (int i = 0; i < subsets.length; i++) {
			int sample = (int) subsets[i];
			
			int posY = ( size.height / 2 ) - sample;
			int negY = ( size.height / 2 ) + sample;
			
			int x = i * boxWidth;
			
			if (boxWidth == 1) {
				g2d.drawLine(x, posY, x, negY);
			} else {
				g2d.setColor(Color.GRAY);
				g2d.fillRect(x + 1, posY + 1, boxWidth - 1, negY - posY - 1);
				g2d.setColor(Color.DARK_GRAY);
				g2d.drawRect(x, posY, boxWidth, negY - posY);
			}
		}
		
		g2d.dispose();
		view.repaint();
		view.requestFocus();
	}
	
	/**
	 * Transcode to Wav
	 * 
	 * @param sourceFile
	 * @param destinationFile
	 * @throws EncoderException
	 */
	private static void transcodeToWav(File sourceFile , File destinationFile) throws Exception {
		//Attributes atters = DefaultAttributes.WAV_PCM_S16LE_STEREO_44KHZ.getAttributes()
		
		//Set Audio Attributes
		AudioAttributes audio = new AudioAttributes();
		audio.setCodec("pcm_s16le");
		audio.setChannels(2);
		audio.setSamplingRate(44100);
		
		//Set encoding attributes
		EncodingAttributes attributes = new EncodingAttributes();
		attributes.setFormat("wav");
		attributes.setAudioAttributes(audio);
		
		//Encode		s
		new Encoder().encode(new MultimediaObject(sourceFile), destinationFile, attributes);
		
	}
	
	/**
	 * Returns the title of the file for example if file name is <b>(club.mp3)</b> it returns <b>(club)</b>
	 *
	 * @param absolutePath
	 *            The File absolute path
	 * @return the File title
	 */
	public static String getFileTitle(String absolutePath) {
		return FilenameUtils.getBaseName(absolutePath);
	}
	
	// handle most WAV and AIFF files
	public static void loadImage() {
		JFileChooser chooser = new JFileChooser();
		int val = chooser.showOpenDialog(null);
		if (val != JFileChooser.APPROVE_OPTION) {
			return;
		}
		
		File file = chooser.getSelectedFile();
		float[] samples;
		
		//Convert to .wav if not ..... ;) be madafucka gangster
		File temporalDecodedFile = null;
		if (! ( "wav".equalsIgnoreCase(getFileTitle(file.getAbsolutePath())) )) {
			//Transcoding to wav
			try {
				temporalDecodedFile = File.createTempFile("decoded_audio", ".wav");
				transcodeToWav(file, temporalDecodedFile);
				temporalDecodedFile.deleteOnExit();
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Fatal exception exiting....");
				System.exit(0);
			}
		}
		
		//Now procceeed orgasm...
		try {
			AudioInputStream in = AudioSystem.getAudioInputStream(temporalDecodedFile);
			AudioFormat fmt = in.getFormat();
			
			if (fmt.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
				throw new UnsupportedAudioFileException("unsigned");
			}
			
			boolean big = fmt.isBigEndian();
			int chans = fmt.getChannels();
			int bits = fmt.getSampleSizeInBits();
			int bytes = bits + 7 >> 3;
			
			int frameLength = (int) in.getFrameLength();
			int bufferLength = chans * bytes * 1024;
			
			samples = new float[frameLength];
			byte[] buf = new byte[bufferLength];
			
			int i = 0;
			int bRead;
			System.out.println(samples.length);
			while ( ( bRead = in.read(buf) ) > -1) {
				
				for (int b = 0; b < bRead;) {
					double sum = 0;
					
					// (sums to mono if multiple channels)
					for (int c = 0; c < chans; c++) {
						if (bytes == 1) {
							sum += buf[b++] << 8;
							
						} else {
							int sample = 0;
							
							// (quantizes to 16-bit)
							if (big) {
								sample |= ( buf[b++] & 0xFF ) << 8;
								sample |= ( buf[b++] & 0xFF );
								b += bytes - 2;
							} else {
								b += bytes - 2;
								sample |= ( buf[b++] & 0xFF );
								sample |= ( buf[b++] & 0xFF ) << 8;
							}
							
							final int sign = 1 << 15;
							final int mask = -1 << 16;
							if ( ( sample & sign ) == sign) {
								sample |= mask;
							}
							
							sum += sample;
						}
					}
					
					samples[i++] = (float) ( sum / chans );
				}
			}
			
		} catch (Exception e) {
			problem(e);
			temporalDecodedFile.delete();
			temporalDecodedFile.deleteOnExit();
			return;
		}
		
		if (img == null) {
			img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		}
		
		drawImage(samples);
		
		//Delete the temporary file
		temporalDecodedFile.delete();
		temporalDecodedFile.deleteOnExit();
	}
	
	static void problem(Object msg) {
		JOptionPane.showMessageDialog(null, String.valueOf(msg));
	}
	
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				JFrame frame = new JFrame("Box Waveform");
				JPanel content = new JPanel(new BorderLayout());
				frame.setContentPane(content);
				
				JButton load = new JButton("Load");
				load.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent ae) {
						loadImage();
					}
				});
				
				view = new JPanel() {
					@Override
					protected void paintComponent(Graphics g) {
						super.paintComponent(g);
						
						if (img != null) {
							g.drawImage(img, 1, 1, img.getWidth(), img.getHeight(), null);
						}
					}
				};
				
				view.setBackground(Color.WHITE);
				view.setPreferredSize(new Dimension(size.width + 2, size.height + 2));
				
				content.add(view, BorderLayout.CENTER);
				content.add(load, BorderLayout.SOUTH);
				
				frame.pack();
				frame.setResizable(false);
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.setLocationRelativeTo(null);
				frame.setVisible(true);
			}
		});
	}
}

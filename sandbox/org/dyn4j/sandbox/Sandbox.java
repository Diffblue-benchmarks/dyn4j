package org.dyn4j.sandbox;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLException;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.ToolTipManager;
import javax.xml.parsers.ParserConfigurationException;

import org.dyn4j.Version;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.World;
import org.dyn4j.dynamics.joint.Joint;
import org.dyn4j.dynamics.joint.MouseJoint;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Transform;
import org.dyn4j.geometry.Vector2;
import org.dyn4j.sandbox.actions.MoveAction;
import org.dyn4j.sandbox.actions.MoveWorldAction;
import org.dyn4j.sandbox.actions.RotateAction;
import org.dyn4j.sandbox.actions.SelectAction;
import org.dyn4j.sandbox.dialogs.AboutDialog;
import org.dyn4j.sandbox.events.BodyActionEvent;
import org.dyn4j.sandbox.input.Keyboard;
import org.dyn4j.sandbox.input.Mouse;
import org.dyn4j.sandbox.panels.WorldTreePanel;
import org.dyn4j.sandbox.persist.XmlFormatter;
import org.dyn4j.sandbox.persist.XmlGenerator;
import org.dyn4j.sandbox.persist.XmlReader;
import org.dyn4j.sandbox.utilities.Fps;
import org.dyn4j.sandbox.utilities.Icons;
import org.dyn4j.sandbox.utilities.RenderState;
import org.dyn4j.sandbox.utilities.RenderUtilities;
import org.xml.sax.SAXException;

import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.gl2.GLUT;

/**
 * Main class for the Sandbox application.
 * @author William Bittle
 * @version 1.0.0
 * @since 1.0.0
 */
public class Sandbox extends JFrame implements GLEventListener, ActionListener {
	/** The version id */
	private static final long serialVersionUID = -7050279589455803564L;

	/** The conversion factor from nano to base */
	private static final double NANO_TO_BASE = 1.0e9;
	
	/** The color for selected bodies/fixtures */
	private static final float[] SELECTED_COLOR = new float[] {0.5f, 0.5f, 1.0f, 1.0f};
	
	/** The sandbox version */
	public static final String VERSION = "1.0.0";
	
	/** The canvas to draw to */
	private GLCanvas canvas;
	
	/** The canvas size */
	private Dimension canvasSize;
	
	/** The OpenGL animator */
	private Animator animator;
	
	/** The glut interface */
	private GLUT glut;
	
	/** The dynamics engine */
	private World world;
	
	/** The time stamp for the last iteration */
	private long last;
	
	/** The paused flag */
	private boolean paused = true;
	
	/** The scale factor */
	private double scale = 32.0;
	
	/** The offset from the origin in world coordinates */
	private Vector2 offset = new Vector2();
	
	/** The keyboard to accept and store key events */
	private Keyboard keyboard;
	
	/** The mouse to accept and store mouse events */
	private Mouse mouse;
	
	/** The frames per second monitor */
	private Fps fps;
	
	/** The rendering state */
	private RenderState renderState;
	
	/** The last path the user chose from either the load or save dialog */
	private File directory;
	
	/** The map to store snapshots of the simulation */
	private Map<String, String> snapshots = new HashMap<String, String>();
	
	// controls
	
	// The menu bar
	
	/** The main menu bar */
	private JMenuBar barMenu;
	
	/** The file menu */
	private JMenu mnuFile;
	
	/** The snapshot menu */
	private JMenu mnuSnapshot;
	
	// The world tree panel
	
	/** The world tree control */
	private WorldTreePanel pnlWorld;
	
	// Simulation toolbar
	
	/** The start button for the simulation */
	private JButton btnStart;
	
	/** The stop button for the simulation */
	private JButton btnStop;
	
	/** Label to show the frames per second */
	private JTextField lblFps;
	
	// Rendering toolbar
	
	/** The random color toggle button */
	private JToggleButton tglRandomColor;
	
	/** The stencil toggle button */
	private JToggleButton tglStencil;
	
	/** The show body labels toggle button */
	private JToggleButton tglBodyLabels;
	
	/** The show fixture labels toggle button */
	private JToggleButton tglFixtureLabels;
	
	/** The anti-aliasing toggle button */
	private JToggleButton tglAntiAliasing;
	
	/** The vertical sync toggle button */
	private JToggleButton tglVerticalSync;
	
	/** The label origin toggle button */
	private JToggleButton tglOriginLabel;
	
	/** The zoom in button */
	private JButton btnZoomIn;
	
	/** The zoom out button */
	private JButton btnZoomOut;
	
	/** The move camera to origin button */
	private JButton btnToOrigin;
	
	// Mouse location toolbar
	
	/** The mouse location label */
	private JTextField lblMouseLocation;
	
	// Actions performed on the OpenGL canvas
	
	// Actions performed on bodies
	
	/** The select body action */
	private SelectAction<SandboxBody> selectBodyAction = new SelectAction<SandboxBody>();
	
	/** The move body action */
	private MoveAction moveBodyAction = new MoveAction();
	
	/** The joint used to move bodies when the simulation is running */
	private MouseJoint selectedBodyJoint;
	
	/** The rotate body action */
	private RotateAction rotateBodyAction = new RotateAction();
	
	// Actions performed on fixtures
	
	/** The edit body action (used to move and rotate fixtures) */
	private SelectAction<SandboxBody> editBodyAction = new SelectAction<SandboxBody>();
	
	/** The select fixture action */
	private SelectAction<BodyFixture> selectFixtureAction = new SelectAction<BodyFixture>();
	
	/** The move fixture action */
	private MoveAction moveFixtureAction = new MoveAction();
	
	/** The rotate fixture action */
	private RotateAction rotateFixtureAction = new RotateAction();
	
	// Actions performed on the world
	
	/** The move world action */
	private MoveWorldAction moveWorldAction = new MoveWorldAction();
	
	/**
	 * Default constructor.
	 */
	public Sandbox() {
		super("Sandbox v" + VERSION + " - dyn4j v" + Version.getVersion());
		
		// make sure tooltips and menus show up on top of the heavy weight canvas
		ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
		JPopupMenu.setDefaultLightWeightPopupEnabled(false);
		
		// set the look and feel to the system look and feel
//		try {
//			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//		} catch (ClassNotFoundException e) {
//			e.printStackTrace();
//		} catch (InstantiationException e) {
//			e.printStackTrace();
//		} catch (IllegalAccessException e) {
//			e.printStackTrace();
//		} catch (UnsupportedLookAndFeelException e) {
//			e.printStackTrace();
//		}
		
		// create the world
		this.world = new World();
		
		// create the keyboard and mouse
		this.keyboard = new Keyboard();
		this.mouse = new Mouse();
		this.fps = new Fps();
		this.renderState = new RenderState();
		
		// create the world tree
		Dimension size = new Dimension(200, 600);
		this.pnlWorld = new WorldTreePanel(this, this.world);
		this.pnlWorld.setPreferredSize(size);
		this.pnlWorld.setMinimumSize(size);
		this.pnlWorld.addActionListener(this);
		
		// create the main menu bar
		
		this.barMenu = new JMenuBar();
		
		// file menu
		this.mnuFile = new JMenu(" File ");
		
		JMenuItem mnuSave = new JMenuItem(" Save Simulation ");
		mnuSave.setIcon(Icons.SAVE);
		mnuSave.setActionCommand("save");
		mnuSave.addActionListener(this);
		
		JMenuItem mnuOpen = new JMenuItem(" Open Simulation ");
		mnuOpen.setIcon(Icons.OPEN);
		mnuOpen.setActionCommand("open");
		mnuOpen.addActionListener(this);
		
		this.mnuFile.add(mnuOpen);
		this.mnuFile.add(mnuSave);
		
		// snapshot menu
		this.mnuSnapshot = new JMenu("Snapshot");
		
		JMenuItem mnuTakeSnapshot = new JMenuItem("Take Snapshot");
		mnuTakeSnapshot.setActionCommand("snapshotTake");
		mnuTakeSnapshot.addActionListener(this);
		mnuTakeSnapshot.setIcon(Icons.SNAPSHOT_TAKE);
		
		JMenuItem mnuClearSnapshots = new JMenuItem("Clear Snapshots");
		mnuClearSnapshots.setActionCommand("snapshotClearAll");
		mnuClearSnapshots.addActionListener(this);
		mnuClearSnapshots.setIcon(Icons.SNAPSHOT_REMOVE);
		
		this.mnuSnapshot.add(mnuTakeSnapshot);
		this.mnuSnapshot.add(mnuClearSnapshots);
		this.mnuSnapshot.addSeparator();
		
		this.barMenu.add(this.mnuFile);
		this.barMenu.add(this.mnuSnapshot);
				
		this.setJMenuBar(this.barMenu);
		
		// create the simulation tool bar
		
		JToolBar barSimulation = new JToolBar("Simulation", JToolBar.HORIZONTAL);
		barSimulation.setRollover(true);
		
		this.btnStart = new JButton(Icons.START);
		this.btnStart.addActionListener(this);
		this.btnStart.setActionCommand("start");
		this.btnStart.setToolTipText("Start Simulation");
		
		this.btnStop = new JButton(Icons.STOP);
		this.btnStop.addActionListener(this);
		this.btnStop.setActionCommand("stop");
		this.btnStop.setToolTipText("Stop Simulation");
		
		this.btnStart.setEnabled(true);
		this.btnStop.setEnabled(false);
		
		barSimulation.add(this.btnStart);
		barSimulation.add(this.btnStop);
		
		this.lblFps = new JTextField();
		this.lblFps.setMaximumSize(new Dimension(70, Short.MAX_VALUE));
		this.lblFps.setHorizontalAlignment(JTextField.RIGHT);
		this.lblFps.setColumns(7);
		this.lblFps.setEditable(false);
		this.lblFps.setToolTipText("Mouse Location (World Coordinates)");
		this.lblFps.setToolTipText("Frames / Second");
		
		barSimulation.addSeparator();
		barSimulation.add(this.lblFps);
		
		// create the settings toolbar
		
		JToolBar barSettings = new JToolBar("Settings", JToolBar.HORIZONTAL);
		barSettings.setRollover(true);
		
		this.tglRandomColor = new JToggleButton(Icons.COLOR);
		this.tglRandomColor.setToolTipText("Enable/Disable Random Body Colors");
		this.tglRandomColor.setActionCommand("color");
		this.tglRandomColor.addActionListener(this);
		this.tglRandomColor.setSelected(ApplicationSettings.isColorRandom());
		
		this.tglStencil = new JToggleButton(Icons.STENCIL);
		this.tglStencil.setToolTipText("Enable/Disable Body Stenciling");
		this.tglStencil.setActionCommand("stencil");
		this.tglStencil.addActionListener(this);
		this.tglStencil.setSelected(ApplicationSettings.isStenciled());
		
		this.tglBodyLabels = new JToggleButton(Icons.BODY_LABEL);
		this.tglBodyLabels.setToolTipText("Enable/Disable Body Labels");
		this.tglBodyLabels.setActionCommand("bodyLabel");
		this.tglBodyLabels.addActionListener(this);
		this.tglBodyLabels.setSelected(ApplicationSettings.isBodyLabeled());
		
		this.tglFixtureLabels = new JToggleButton(Icons.FIXTURE_LABEL);
		this.tglFixtureLabels.setToolTipText("Enable/Disable Fixture Labels");
		this.tglFixtureLabels.setActionCommand("fixtureLabel");
		this.tglFixtureLabels.addActionListener(this);
		this.tglFixtureLabels.setSelected(ApplicationSettings.isFixtureLabeled());
		
		this.tglAntiAliasing = new JToggleButton(Icons.AA);
		this.tglAntiAliasing.setToolTipText("Enable/Disable Anti-Aliasing");
		this.tglAntiAliasing.setActionCommand("aa");
		this.tglAntiAliasing.addActionListener(this);
		this.tglAntiAliasing.setSelected(ApplicationSettings.isAntiAliasingEnabled());
		
		this.tglVerticalSync = new JToggleButton(Icons.SYNC);
		this.tglVerticalSync.setToolTipText("Enable/Disable Vertical Sync");
		this.tglVerticalSync.setActionCommand("vertical-sync");
		this.tglVerticalSync.addActionListener(this);
		this.tglVerticalSync.setSelected(ApplicationSettings.isVerticalSyncEnabled());
		
		this.tglOriginLabel = new JToggleButton(Icons.ORIGIN);
		this.tglOriginLabel.setToolTipText("Enable/Disable Origin Label");
		this.tglOriginLabel.setActionCommand("origin");
		this.tglOriginLabel.addActionListener(this);
		this.tglOriginLabel.setSelected(ApplicationSettings.isOriginLabeled());
		
		this.btnZoomIn = new JButton(Icons.ZOOM_IN);
		this.btnZoomIn.setToolTipText("Zoom In");
		this.btnZoomIn.setActionCommand("zoom-in");
		this.btnZoomIn.addActionListener(this);
		
		this.btnZoomOut = new JButton(Icons.ZOOM_OUT);
		this.btnZoomOut.setToolTipText("Zoom Out");
		this.btnZoomOut.setActionCommand("zoom-out");
		this.btnZoomOut.addActionListener(this);
		
		this.btnToOrigin = new JButton(Icons.TO_ORIGIN);
		this.btnToOrigin.setToolTipText("Center the camera on the origin.");
		this.btnToOrigin.setActionCommand("to-origin");
		this.btnToOrigin.addActionListener(this);
		
		barSettings.add(this.tglRandomColor);
		barSettings.add(this.tglStencil);
		barSettings.add(this.tglBodyLabels);
		barSettings.add(this.tglFixtureLabels);
		barSettings.add(this.tglAntiAliasing);
		barSettings.add(this.tglVerticalSync);
		barSettings.add(this.tglOriginLabel);
		barSettings.add(this.btnZoomIn);
		barSettings.add(this.btnZoomOut);
		barSettings.add(this.btnToOrigin);
		
		// create the mouse location toolbar
		
		JToolBar barMouseLocation = new JToolBar("Mouse Location", JToolBar.HORIZONTAL);
		barMouseLocation.setFloatable(true);
		
		this.lblMouseLocation = new JTextField();
		this.lblMouseLocation.setHorizontalAlignment(JTextField.RIGHT);
		this.lblMouseLocation.setColumns(10);
		this.lblMouseLocation.setEditable(false);
		this.lblMouseLocation.setToolTipText("Mouse Location (World Coordinates)");
		
		barMouseLocation.add(this.lblMouseLocation);
		
		// help toolbar
		
		JToolBar barAbout = new JToolBar("About", JToolBar.HORIZONTAL);
		barAbout.setFloatable(true);
		
		JButton btnAbout = new JButton();
		btnAbout.setIcon(Icons.ABOUT);
		btnAbout.setToolTipText("About Sandbox");
		btnAbout.setActionCommand("about");
		btnAbout.addActionListener(this);
		
		barAbout.add(btnAbout);
		
		// add the toolbars to the layout
		
		JPanel pnlToolBar = new JPanel();
		pnlToolBar.setLayout(new BoxLayout(pnlToolBar, BoxLayout.X_AXIS));
		pnlToolBar.add(barSimulation);
		pnlToolBar.add(barSettings);
		pnlToolBar.add(Box.createHorizontalGlue());
		pnlToolBar.add(barMouseLocation);
		pnlToolBar.add(barAbout);
		pnlToolBar.setMaximumSize(pnlToolBar.getPreferredSize());
				
		// attempt to set the icon
		this.setIconImage(Icons.SANDBOX_48.getImage());
		
		// setup OpenGL capabilities
		if (!GLProfile.isGL3Available()) {
			throw new GLException("OpenGL 3.0 or higher is required");
		}
		GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL2));
		caps.setDoubleBuffered(true);
		// setup the stencil buffer to outline shapes
		caps.setStencilBits(1);
		// setting multisampling allows for better looking body outlines
		caps.setSampleBuffers(true);
		caps.setNumSamples(2);
		caps.setHardwareAccelerated(true);
		
		this.glut = new GLUT();
		
		this.canvasSize = new Dimension(600, 600);
		// create a canvas to paint to 
		this.canvas = new GLCanvas(caps);
		this.canvas.setPreferredSize(this.canvasSize);
		this.canvas.setMinimumSize(this.canvasSize);
		this.canvas.setIgnoreRepaint(true);
		// add this class as the gl event listener
		this.canvas.addGLEventListener(this);
		
		// add the mouse and keyboard listeners
		this.canvas.addKeyListener(this.keyboard);
		this.canvas.addMouseListener(this.mouse);
		this.canvas.addMouseMotionListener(this.mouse);
		this.canvas.addMouseWheelListener(this.mouse);
		
		// placing the GLCanvas in a JPanel allows the JSplitPane
		// to not cause an exception when the user moves the split
		JPanel pnlTest = new JPanel();
		pnlTest.setLayout(new BorderLayout());
		pnlTest.add(this.canvas);
		
		// add a split pane
		JSplitPane pneSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.pnlWorld, pnlTest);
		
		// setup the layout
		Container container = this.getContentPane();
		
		GroupLayout layout = new GroupLayout(container);
		container.setLayout(layout);
		
		layout.setAutoCreateGaps(true);
		layout.setHorizontalGroup(layout.createParallelGroup()
				.addComponent(pnlToolBar, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
				.addComponent(pneSplit));
		layout.setVerticalGroup(layout.createSequentialGroup()
				.addComponent(pnlToolBar)
				.addComponent(pneSplit));
		
		// size everything
		this.pack();
		
		// move from (0, 0) since this hides some of the window frame
		this.setLocation(10, 10);
		
		// show the window
		this.setVisible(true);
		
		// setting this property will call the dispose methods on the GLCanvas
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		// initialize the last update time
		this.last = System.nanoTime();
		
		// create an animator to animated the canvas
		this.animator = new Animator(this.canvas);
		this.animator.setRunAsFastAsPossible(false);
		this.animator.start();
	}
	
	/**
	 * Start active rendering the example.
	 */
	public void start() {
		// start the animator
		this.animator.start();
	}
	
	/**
	 * Stops the animator thread from running.
	 */
	public void stop() {
		this.animator.stop();
	}
	
	/* (non-Javadoc)
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public void actionPerformed(ActionEvent event) {
		String command = event.getActionCommand();
		// World tree panel events
		if ("clear-all".equals(command)) {
			// clear the current selection
			this.endAllActions();
		} else if ("remove-body".equals(command)) {
			// check if it was this body
			BodyActionEvent bae = (BodyActionEvent)event;
			SandboxBody body = bae.getBody();
			// check the select/edit body actions
			if (body == this.selectBodyAction.getObject()
			 || body == this.editBodyAction.getObject()) {
				// if the body is selected or being edited then end the actions
				this.endAllActions();
			}
		}
		// Sandbox events
		if ("save".equals(command)) {
			try {
				this.saveSimulationAction();
			} catch (IOException e) {
				JOptionPane.showMessageDialog(
						this,
						"An Error occured when trying to save the simulation:\n" + e.getMessage(),
						"Error Saving Simulation",
						JOptionPane.ERROR_MESSAGE);
			}
		} else if ("open".equals(command)) {
			try {
				this.openSimulationAction();
				// stop all actions
				this.endAllActions();
			} catch (Exception e) {
				JOptionPane.showMessageDialog(
						this,
						"An Error occured when trying to open the selected simulation file:\n" + e,
						"Error Opening Simulation",
						JOptionPane.ERROR_MESSAGE);
			}
		} else if ("start".equals(command)) {
			if (isPaused()) {
				// stop all actions
				this.endAllActions();
				// take a snapshot of the current simulation
				this.takeSnapshot(true);
				// disable the world editor
				this.pnlWorld.setEnabled(false);
				this.btnStart.setEnabled(false);
				this.btnStop.setEnabled(true);
				this.mnuFile.setEnabled(false);
				this.mnuSnapshot.setEnabled(false);
				setPaused(false);
			}
		} else if ("stop".equals(command)) {
			if (!isPaused()) {
				// stop all actions
				this.endAllActions();
				// clear the mouse joint
				this.selectedBodyJoint = null;
				// enable the world editor
				this.pnlWorld.setEnabled(true);
				this.btnStart.setEnabled(true);
				this.btnStop.setEnabled(false);
				this.mnuFile.setEnabled(true);
				this.mnuSnapshot.setEnabled(true);
				setPaused(true);
			}
		} else if ("color".equals(command)) {
			ApplicationSettings.setColorRandom(!ApplicationSettings.isColorRandom());
		} else if ("stencil".equals(command)) {
			ApplicationSettings.setStenciled(!ApplicationSettings.isStenciled());
		} else if ("bodyLabel".equals(command)) {
			ApplicationSettings.setBodyLabeled(!ApplicationSettings.isBodyLabeled());
		} else if ("fixtureLabel".equals(command)) {
			ApplicationSettings.setFixtureLabeled(!ApplicationSettings.isFixtureLabeled());
		} else if ("aa".equals(command)) {
			ApplicationSettings.setAntiAliasingEnabled(!ApplicationSettings.isAntiAliasingEnabled());
		} else if ("vertical-sync".equals(command)) {
			ApplicationSettings.setVerticalSyncEnabled(!ApplicationSettings.isVerticalSyncEnabled());
		} else if ("origin".equals(command)) {
			ApplicationSettings.setOriginLabeled(!ApplicationSettings.isOriginLabeled());
		} else if ("zoom-in".equals(command)) {
			this.scale *= 0.5;
		} else if ("zoom-out".equals(command)) {
			this.scale *= 2.0;
		} else if ("about".equals(command)) {
			AboutDialog.show(this);
		} else if ("to-origin".equals(command)) {
			this.offset.zero();
		} else if ("snapshotTake".equals(command)) {
			this.takeSnapshot(false);
		} else if ("snapshotClearAll".equals(command)) {
			// make sure they are sure
			int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear all snapshots?", "Clear Snapshots", JOptionPane.YES_NO_CANCEL_OPTION);
			// check the user's choice
			if (choice == JOptionPane.YES_OPTION) {
				// remove the entries from the map
				this.snapshots.clear();
				// remove all components after the third one
				while (this.mnuSnapshot.getItemCount() > 3) {
					this.mnuSnapshot.remove(3);
				}
			}
		} else if ("snapshotRestore".equals(command)) {
			JMenuItem item = (JMenuItem)event.getSource();
			String key = item.getText();
			
			// make sure they are sure
			int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want load the '" + key + "' snapshot?\n" +
					"The current simulation will be lost.", "Load Snapshot", JOptionPane.YES_NO_CANCEL_OPTION);
			// check the user's choice
			if (choice == JOptionPane.YES_OPTION) {
				try {
					this.restoreSnapshot(key);
					// stop all actions
					this.endAllActions();
				} catch (Exception e) {
					JOptionPane.showMessageDialog(
							this,
							"An Error occured when trying to restore the snapshot '" + key + "':\n" + e,
							"Error Restoring Snapshot",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see javax.media.opengl.GLEventListener#init(javax.media.opengl.GLAutoDrawable)
	 */
	@Override
	public void init(GLAutoDrawable glDrawable) {
		// get the OpenGL context
		GL2 gl = glDrawable.getGL().getGL2();
		
		int[] temp = new int[1];
		gl.glGetIntegerv(GL.GL_STENCIL_BITS, temp, 0);
		if (temp[0] <= 0) {
			// disable the stencil button
			this.tglStencil.setEnabled(false);
		}
		
		// set the matrix mode to projection
		gl.glMatrixMode(GL2.GL_PROJECTION);
		// initialize the matrix
		gl.glLoadIdentity();
		// set the view to a 2D view
		gl.glOrtho(-300, 300, -300, 300, 0, 1);
		
		// switch to the model view matrix
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		// initialize the matrix
		gl.glLoadIdentity();
		
		// set the clear color to white
		gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
		// set the stencil clear value
		gl.glClearStencil(0);
		
		// disable depth testing since we are working in 2D
		gl.glDisable(GL.GL_DEPTH_TEST);
		// we dont need lighting either
		gl.glDisable(GL2.GL_LIGHTING);
		
		// enable blending for translucency
		gl.glEnable(GL.GL_BLEND);
		gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
				
		// set the swap interval to vertical-sync
		gl.setSwapInterval(1);
	}
	
	/* (non-Javadoc)
	 * @see javax.media.opengl.GLEventListener#display(javax.media.opengl.GLAutoDrawable)
	 */
	@Override
	public void display(GLAutoDrawable glDrawable) {
		// get the OpenGL context
		GL2 gl = glDrawable.getGL().getGL2();

		// turn on/off multi-sampling
		if (ApplicationSettings.isAntiAliasingEnabled()) {
			gl.glEnable(GL.GL_MULTISAMPLE);
		} else {
			gl.glDisable(GL.GL_MULTISAMPLE);
		}
		
		// turn on/off vertical sync
		if (ApplicationSettings.isVerticalSyncEnabled()) {
			gl.setSwapInterval(1);
		} else {
			gl.setSwapInterval(0);
		}
		
		// clear the screen
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_STENCIL_BUFFER_BIT);
		// switch to the model view matrix
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		// initialize the matrix (0,0) is in the center of the window
		gl.glLoadIdentity();
		
		// the main loop
		{
			// perform other operations at the end (it really
			// doesn't matter if its done at the start or end)
			this.update();
			
			// render the scene
			this.render(gl);
			
			// look for input
			this.poll();
		}
	}
	
	/* (non-Javadoc)
	 * @see javax.media.opengl.GLEventListener#dispose(javax.media.opengl.GLAutoDrawable)
	 */
	@Override
	public void dispose(GLAutoDrawable glDrawable) {
		// nothing to dispose from OpenGL right now
	}
	
	/* (non-Javadoc)
	 * @see javax.media.opengl.GLEventListener#reshape(javax.media.opengl.GLAutoDrawable, int, int, int, int)
	 */
	@Override
	public void reshape(GLAutoDrawable glDrawable, int x, int y, int width, int height) {
		// get the OpenGL context
		GL2 gl = glDrawable.getGL().getGL2();
		
		// resize the ortho view
		
		// set the matrix mode to projection
		gl.glMatrixMode(GL2.GL_PROJECTION);
		// initialize the matrix
		gl.glLoadIdentity();
		// set the view to a 2D view
		gl.glOrtho(-width / 2.0, width / 2.0, -height / 2.0, height / 2.0, 0, 1);
		
		// switch to the model view matrix
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		// initialize the matrix
		gl.glLoadIdentity();
		
		// set the size
		this.canvasSize = new Dimension(width, height);
	}

	/**
	 * Updates the world.
	 */
	private void update() {
		// get the current time
        long time = System.nanoTime();
        // get the elapsed time from the last iteration
        long diff = time - this.last;
        // set the last time
        this.last = time;
        
		// check if the state is paused
		if (!this.isPaused()) {
	    	// convert from nanoseconds to seconds
	    	double elapsedTime = (double)diff / NANO_TO_BASE;
	    	// obtain the lock on the world object
	    	synchronized (this.world) {
		        // update the world with the elapsed time
		        this.world.update(elapsedTime);
			}
		}
		
		// set the render state
		this.renderState.scale = scale;
		synchronized (this.world) {
			this.renderState.dt = this.world.getStep().getDeltaTime();
			this.renderState.invDt = this.world.getStep().getInverseDeltaTime();
		}
		this.renderState.size = this.canvasSize;
		this.renderState.offset = this.offset.copy();
		
		this.fps.update(diff);
		this.lblFps.setText(this.fps.getFpsString());
	}
	
	/**
	 * Renders the world.
	 * @param gl the OpenGL surface
	 */
	private void render(GL2 gl) {
		Dimension size = this.renderState.size;
		Vector2 offset = this.renderState.offset;
		double scale = this.renderState.scale;
		
		// apply a scaling transformation
		gl.glPushMatrix();
		gl.glScaled(scale, scale, scale);
		gl.glTranslated(offset.x, offset.y, 0.0);
		
		// draw selected stuff
		
		synchronized (this.world) {
			// render all the bodies in the world
			int bSize = this.world.getBodyCount();
			for (int i = 0; i < bSize; i++) {
				SandboxBody body = (SandboxBody)this.world.getBody(i);
				// dont draw the selected body
				if (body == this.selectBodyAction.getObject()) continue;
				if (body == this.editBodyAction.getObject()) continue;
				if (ApplicationSettings.isStenciled()) {
					RenderUtilities.outlineBody(gl, body, this.renderState, true);
				} else {
					body.render(gl);
				}
			}
			
			// render the joints
			int jSize = this.world.getJointCount();
			for (int i = 0; i < jSize; i++) {
				Joint joint = this.world.getJoint(i);
				// dont draw the mouse joint used during simulation since its drawn again
				if (joint == this.selectedBodyJoint) continue;
				// otherwise draw the joint normally
				RenderUtilities.drawJoint(gl, joint, this.renderState);
			}
		}
		
		if (this.selectBodyAction.isActive()) {
			SandboxBody body = this.selectBodyAction.getObject();
			if (ApplicationSettings.isStenciled()) {
				// stenciling requires a larger radius
				RenderUtilities.outlineShapes(gl, body, 6, SELECTED_COLOR, this.renderState);
				gl.glColor4fv(body.getFillColor(), 0);
				body.fill(gl);
			} else {
				RenderUtilities.outlineShapes(gl, body, 4, SELECTED_COLOR, this.renderState);
				body.render(gl);
			}
		}
		
		if (this.editBodyAction.isActive()) {
			SandboxBody body = this.editBodyAction.getObject();
			// overlay everything but this shape
			gl.glColor4f(0.0f, 0.0f, 0.0f, 0.5f);
			gl.glPushMatrix();
			gl.glLoadIdentity();
			RenderUtilities.fillRectangleFromTopLeft(gl, -size.width * 0.5, size.height * 0.5, size.width, size.height);
			gl.glPopMatrix();
			// redraw the shape on top of the overlay
			body.render(gl);
			// see if a fixture is selected
			if (this.selectFixtureAction.isActive()) {
				BodyFixture bf = this.selectFixtureAction.getObject();
				Convex convex = bf.getShape();
				RenderUtilities.outlineShape(gl, convex, body.getTransform(), 4, SELECTED_COLOR);
				gl.glPushMatrix();
				RenderUtilities.applyTransform(gl, body.getTransform());
				gl.glColor4fv(body.getFillColor(), 0);
				RenderUtilities.fillShape(gl, convex);
				gl.glColor4fv(body.getOutlineColor(), 0);
				RenderUtilities.drawShape(gl, convex, false);
				gl.glPopMatrix();
			}
		}
		
		// draw the mouse joint last always
		if (this.selectedBodyJoint != null) {
			RenderUtilities.drawMouseJoint(gl, this.selectedBodyJoint, this.renderState);
		}
		
		gl.glPopMatrix();
		
		// draw other stuff
		
		gl.glPushMatrix();
		gl.glLoadIdentity();
		
		// draw origin label
		
		if (ApplicationSettings.isOriginLabeled()) {
			double ox = offset.x * scale;
			double oy = offset.y * scale;
			gl.glColor3f(0.0f, 0.0f, 0.0f);
			RenderUtilities.fillRectangleFromCenter(gl, ox, oy, 3, 3);
			gl.glRasterPos2d(2 + ox, -12 + oy);
			this.glut.glutBitmapString(GLUT.BITMAP_HELVETICA_10, "Origin");
		}

		// draw labels over the origin label
		boolean bodyLabels = ApplicationSettings.isBodyLabeled();
		boolean fixtureLabels = ApplicationSettings.isFixtureLabeled();
		if ((bodyLabels || fixtureLabels) && !this.editBodyAction.isActive()) {
			synchronized (world) {
				gl.glColor3f(0.0f, 0.0f, 0.0f);
				// render all the bodies in the world
				int bSize = this.world.getBodyCount();
				for (int i = 0; i < bSize; i++) {
					SandboxBody body = (SandboxBody)this.world.getBody(i);
					RenderUtilities.drawLabels(gl, glut, body, this.renderState, 5, bodyLabels, fixtureLabels);
				}
			}
		}
		
		// draw HUD like stuff
		
		// translate (0, 0) to the bottom left corner
		gl.glTranslated(-size.getWidth() * 0.5, -size.getHeight() * 0.5, 0.0);
		RenderUtilities.drawPixelScale(gl, this.glut, 5, 18, 3, 100, 15, this.renderState);
		
		gl.glPopMatrix();
	}
	
	/**
	 * Polls for input from the user.
	 */
	public void poll() {
		Dimension size = this.renderState.size;
		Vector2 offset = this.renderState.offset;
		double scale = this.renderState.scale;
		
		// see if the user has zoomed in or not
		if (this.mouse.hasScrolled()) {
			// get the scroll amount
			int scroll = this.mouse.getScrollAmount();
			// zoom in or out
			if (scroll < 0) {
				this.scale *= 0.5;
			} else {
				this.scale *= 2.0;
			}
		}
		
		// get the mouse location
		Point p = this.mouse.getLocation();
		if (p == null) {
			p = new Point();
		}
		Vector2 pw = this.screenToWorld(p, size, offset, scale);
		
		// update the mouse location
		if (this.mouse.hasMoved()) {
			this.lblMouseLocation.setText(RenderUtilities.formatVector2(pw));
		}
		
		// the mouse button 1 or 3 was clicked
		if (this.mouse.wasClicked(MouseEvent.BUTTON1) || this.mouse.wasClicked(MouseEvent.BUTTON3)) {
			// check if a body is already clicked
			if (this.selectBodyAction.isActive()) {
				// get the body
				SandboxBody body = this.selectBodyAction.getObject();
				// get the fixture
				BodyFixture fixture = this.getFixtureAtPoint(body, pw);
				// make sure the click was inside the same body
				if (fixture == null) {
					// otherwise de-select the body
					this.selectBodyAction.end();
					this.editBodyAction.end();
				}
			} else if (this.editBodyAction.isActive()) {
				// get the body
				SandboxBody body = this.editBodyAction.getObject();
				// get the fixture
				BodyFixture fixture = this.getFixtureAtPoint(body, pw);
				// see if a fixture was clicked
				if (fixture != null) {
					// start the select fixture action
					this.selectFixtureAction.begin(fixture);
				} else {
					// end the edit body action
					this.editBodyAction.end();
					this.selectFixtureAction.end();
				}
			} else {
				// dont allow selection of any kind unless its mouse button 1 (creates
				// a mouse joint if running) or the simulation is paused
				if (this.mouse.wasClicked(MouseEvent.BUTTON1) || this.isPaused()) {
					// otherwise see if a body is being selected
					SandboxBody body = this.getBodyAtPoint(pw);
					// check for null
					if (body != null) {
						// begin the select body action
						this.selectBodyAction.begin(body);
					}
				}
			}
		}
		
		// the mouse button 1 is pressed and being held
		if (this.mouse.isPressed(MouseEvent.BUTTON1)) {
			// check if a body is already selected
			if (this.selectBodyAction.isActive() && this.moveBodyAction.isActive()) {
				// get the body
				SandboxBody body = this.selectBodyAction.getObject();
				// check if the world is running
				if (this.isPaused()) {
					// get the difference
					Vector2 tx = pw.difference(this.moveBodyAction.getBeginPosition());
					// move the body with the mouse
					body.translate(tx);
					// update the action
					this.moveBodyAction.update(pw);
				} else {
					// update the mouse joint's target point
					if (this.selectedBodyJoint == null) {
						this.selectedBodyJoint = new MouseJoint(body, pw, 4.0, 0.7, 1000.0 * body.getMass().getMass());
						synchronized (this.world) {
							this.world.add(this.selectedBodyJoint);
						}
					} else {
						this.selectedBodyJoint.setTarget(pw);
					}
				}
			} else if (this.moveWorldAction.isActive()) {
				// then translate the offset
				// we need to get the current position in world coordinates using the old offset
				Vector2 pwt = this.screenToWorld(p, size, this.moveWorldAction.getOffset(), scale);
				// compute the difference in the new position to get the offset
				Vector2 tx = pwt.difference(this.moveWorldAction.getBeginPosition());
				// apply it to the offset
				this.offset.add(tx);
				// update the new mouse position
				this.moveWorldAction.update(pwt);
			} else if (this.editBodyAction.isActive()) {
				// get the body
				SandboxBody body = this.editBodyAction.getObject();
				// see if the move fixture action is active
				if (this.selectFixtureAction.isActive() && this.moveFixtureAction.isActive()) {
					// get the fixture
					BodyFixture bf = this.selectFixtureAction.getObject();
					// get the local pw
					Vector2 lpw = body.getTransform().getInverseTransformed(pw);
					// get the difference
					Vector2 tx = lpw.difference(this.moveFixtureAction.getBeginPosition());
					// move the body with the mouse
					bf.getShape().translate(tx);
					// update the action
					this.moveFixtureAction.update(lpw);
				} else {
					// get the current fixture that the mouse is on
					BodyFixture fixture = this.getFixtureAtPoint(body, pw);
					// see if a fixture was clicked
					if (fixture != null) {
						// start the select fixture action
						this.selectFixtureAction.begin(fixture);
						this.moveFixtureAction.begin(body.getTransform().getInverseTransformed(pw));
					} else {
						// end the edit body action
						this.editBodyAction.end();
						this.selectFixtureAction.end();
					}
				}
			} else {
				// otherwise see if a body is being selected
				SandboxBody body = this.getBodyAtPoint(pw);
				// check for null
				if (body != null) {
					// begin the select body action
					this.selectBodyAction.begin(body);
					// begin the move body action
					this.moveBodyAction.begin(pw);
					this.editBodyAction.end();
				} else {
					// then assume the user wants to move the world
					this.moveWorldAction.begin(this.offset.copy(), pw, this);
				}
			}
		}
		
		// the mouse button 1 was double clicked
		if (this.mouse.wasDoubleClicked(MouseEvent.BUTTON1)) {
			// dont allow editing during simulation
			if (this.isPaused()) {
				SandboxBody body = this.getBodyAtPoint(pw);
				// check for null
				if (body != null) {
					// end the body selection action
					this.selectBodyAction.end();
					// begin the edit body action
					this.editBodyAction.begin(body);
				} else {
					// then assume the user is done editing fixtures on the body
					this.editBodyAction.end();
				}
			}
		}
		
		// the mouse button 3 is pressed and being held
		if (this.mouse.isPressed(MouseEvent.BUTTON3)) {
			// check if a body is already selected
			if (this.selectBodyAction.isActive() && this.rotateBodyAction.isActive()) {
				// get the body
				SandboxBody body = this.selectBodyAction.getObject();
				// get the rotation
				Vector2 c = body.getWorldCenter();
				Vector2 v1 = c.to(this.rotateBodyAction.getBeginPosition());
				Vector2 v2 = c.to(pw);
				double theta = v1.getAngleBetween(v2);
				// move the body with the mouse
				body.rotate(theta, c);
				// update the action
				this.rotateBodyAction.update(pw);
			} else if (this.editBodyAction.isActive()) {
				// get the body
				SandboxBody body = this.editBodyAction.getObject();
				// see if the move fixture action is active
				if (this.selectFixtureAction.isActive() && this.rotateFixtureAction.isActive()) {
					// get the fixture
					BodyFixture bf = this.selectFixtureAction.getObject();
					Convex convex = bf.getShape();
					Vector2 c = convex.getCenter();
					Vector2 lpw = body.getTransform().getInverseTransformed(pw);
					// get the rotation
					Vector2 v1 = c.to(this.rotateFixtureAction.getBeginPosition());
					Vector2 v2 = c.to(lpw);
					double theta = v1.getAngleBetween(v2);
					// rotate the fixture about the local center
					bf.getShape().rotate(theta, convex.getCenter());
					// update the action
					this.rotateFixtureAction.update(lpw);
				} else {
					// get the current fixture that the mouse is on
					BodyFixture fixture = this.getFixtureAtPoint(body, pw);
					// see if a fixture was clicked
					if (fixture != null) {
						// start the select fixture action
						this.selectFixtureAction.begin(fixture);
						this.rotateFixtureAction.begin(body.getTransform().getInverseTransformed(pw));
					} else {
						// end the edit body action
						this.editBodyAction.end();
						this.selectFixtureAction.end();
					}
				}
			} else {
				// dont allow rotating while simulating
				if (this.isPaused()) {
					// otherwise see if a body is being selected
					SandboxBody body = this.getBodyAtPoint(pw);
					// check for null
					if (body != null) {
						// begin the select body action
						this.selectBodyAction.begin(body);
						// begin the move body action
						this.rotateBodyAction.begin(pw);
					}
				}
			}
		}
		
		// check if the mouse button 1 was released
		if (this.mouse.wasReleased(MouseEvent.BUTTON1)) {
			if (this.moveBodyAction.isActive()) {
				// end the action
				this.moveBodyAction.end();
				// end the move body joint
				if (this.selectedBodyJoint != null) {
					synchronized (this.world) {
						this.world.remove(this.selectedBodyJoint);
					}
					this.selectedBodyJoint = null;
				}
			}
			if (this.moveFixtureAction.isActive()) {
				SandboxBody body = this.editBodyAction.getObject();
				// recompute the mass if the position changes
				body.setMass(body.getMass().getType());
				this.moveFixtureAction.end();
			}
			if (this.moveWorldAction.isActive()) {
				// end the action
				this.moveWorldAction.end(this);
			}
		}
		
		// check if the mouse button 3 was released
		if (this.mouse.wasReleased(MouseEvent.BUTTON3)) {
			if (this.rotateBodyAction.isActive()) {
				// end the action
				this.rotateBodyAction.end();
			}
			if (this.rotateFixtureAction.isActive()) {
				this.rotateFixtureAction.end();
			}
		}
		
		this.mouse.clear();
	}
	
	/**
	 * Returns true if the simulation is paused.
	 * @return boolean
	 */
	public synchronized boolean isPaused() {
		return this.paused;
	}
	
	/**
	 * Sets the paused flag of the simulation.
	 * @param flag true if the simulation should be paused
	 */
	public synchronized void setPaused(boolean flag) {
		this.paused = flag;
		if (!flag) {
			// if the simulation is being unpaused then
			// reset the last time
			this.last = System.nanoTime();
		}
	}
	
	/**
	 * Returns the first body in the world's body list to contain the given point.
	 * <p>
	 * If no body is found at the given point, null is returned.
	 * @param point the world space point
	 * @return {@link SandboxBody}
	 */
	private SandboxBody getBodyAtPoint(Vector2 point) {
		int bSize = this.world.getBodyCount();
		// check if there is already a selected body
		if (this.selectBodyAction.isActive()) {
			// if so, then check that body first
			SandboxBody body = this.selectBodyAction.getObject();
			if (this.contains(body, point)) {
				return body;
			}
		}
		// if the selected body doesnt contain the point
		// then check the other bodies in the world
		// loop over all the bodies in the world
		for (int i = bSize - 1; i >= 0; i--) {
			Body body = this.world.getBody(i);
			// does the body contain the point
			if (contains(body, point)) {
				return (SandboxBody) body;
			}
		}
		return null;
	}
	
	/**
	 * Returns true if the given body contains the given point.
	 * @param point the world space point
	 * @param body the body
	 * @return boolean
	 */
	private boolean contains(Body body, Vector2 point) {
		Transform transform = body.getTransform();
		int fSize = body.getFixtureCount();
		// loop over the body fixtures
		for (int j = fSize - 1; j >= 0; j--) {
			BodyFixture bodyFixture = body.getFixture(j);
			Convex convex = bodyFixture.getShape();
			if (convex.contains(point, transform)) {
				// return the first body who contains a fixture
				// that contains the given point
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Returns the fixture of the given body that the given point is inside.
	 * @param point the world space point
	 * @param body the body
	 * @return BodyFixture
	 */
	private BodyFixture getFixtureAtPoint(Body body, Vector2 point) {
		Transform transform = body.getTransform();
		int fSize = body.getFixtureCount();
		// check if a fixture is already selected
		if (this.selectFixtureAction.isActive()) {
			// if so, then check the selected fixture first
			BodyFixture bodyFixture = this.selectFixtureAction.getObject();
			Convex convex = bodyFixture.getShape();
			if (convex.contains(point, transform)) {
				return bodyFixture;
			}
		}
		// otherwise check the other fixtures on the body
		// loop over the body fixtures
		for (int j = fSize - 1; j >= 0; j--) {
			BodyFixture bodyFixture = body.getFixture(j);
			Convex convex = bodyFixture.getShape();
			if (convex.contains(point, transform)) {
				// return the first body who contains a fixture
				// that contains the given point
				return bodyFixture;
			}
		}
		return null;
	}
	
	/**
	 * Converts from screen coordinates to world coordinates.
	 * @param p the screen point
	 * @param size the canvas size
	 * @param offset the canvas offset
	 * @param scale the screen to world scale factor
	 * @return Vector2 the world point
	 */
	private Vector2 screenToWorld(Point p, Dimension size, Vector2 offset, double scale) {
		Vector2 v = new Vector2();
		double x = p.x;
		double y = p.y;
		double w = size.getWidth();
		double h = size.getHeight();
		double ox = offset.x;
		double oy = offset.y;
		v.x = (x - w * 0.5) / scale - ox;
		v.y = -((y - h * 0.5) / scale + oy);
		return v;
	}
	
	/**
	 * Saves the current simulation state to an xml file.
	 * @throws IOException thrown if an IO error occurs
	 */
	private void saveSimulationAction() throws IOException {
		String xml = "";
		synchronized (this.world) {
			xml = XmlGenerator.toXml(this.world);
		}
		XmlFormatter formatter = new XmlFormatter(2);
		xml = formatter.format(xml);
		
		// create a class to show a "are you sure" message when over writing an existing file
		JFileChooser fileBrowser = new JFileChooser() {
			/** The version id */
			private static final long serialVersionUID = 1L;

			/* (non-Javadoc)
			 * @see javax.swing.JFileChooser#approveSelection()
			 */
			@Override
			public void approveSelection() {
				if (getDialogType() == SAVE_DIALOG) {
					File selectedFile = getSelectedFile();
					if ((selectedFile != null) && selectedFile.exists()) {
						int response = JOptionPane.showConfirmDialog(
										this,
										"The file " + selectedFile.getName() + " already exists. Do you want to replace the existing file?",
										"Ovewrite file",
										JOptionPane.YES_NO_OPTION,
										JOptionPane.WARNING_MESSAGE);
						if (response != JOptionPane.YES_OPTION)
							return;
					}
				}

				super.approveSelection();
			}
		};
		fileBrowser.setMultiSelectionEnabled(false);
		if (this.directory != null) {
			fileBrowser.setCurrentDirectory(this.directory);
		}
		fileBrowser.setSelectedFile(new File("simulation.xml"));
		int option = fileBrowser.showSaveDialog(this);
		// check the option
		if (option == JFileChooser.APPROVE_OPTION) {
			File file = fileBrowser.getSelectedFile();
			if (file.isFile()) {
				this.directory = file.getParentFile();
				// see if its a new one or it already exists
				if (file.exists()) {
					// overwrite the file
					FileWriter fw = new FileWriter(file);
					fw.write(xml);
					fw.close();
					JOptionPane.showMessageDialog(this, "File saved successfully!", "File Saved", JOptionPane.INFORMATION_MESSAGE);
				} else {
					// create a new file
					if (file.createNewFile()) {
						FileWriter fw = new FileWriter(file);
						fw.write(xml);
						fw.close();
						JOptionPane.showMessageDialog(this, "File saved successfully!", "File Saved", JOptionPane.INFORMATION_MESSAGE);
					}
				}
			} else {
				this.directory = file;
				// its a directory, so save it inside the dir
				// using the default name
				String path = file.getPath();
				File newFile = new File(path);
				if (newFile.createNewFile()) {
					FileWriter fw = new FileWriter(file);
					fw.write(xml);
					fw.close();
					JOptionPane.showMessageDialog(this, "File saved successfully!", "File Saved", JOptionPane.INFORMATION_MESSAGE);
				}
				
			}
		}
	}
	
	/**
	 * Attempts to open and load the simulation state from the user selected file.
	 * @throws ParserConfigurationException thrown if an error occurs in configuring the SAX parser
	 * @throws SAXException thrown if any parsing error is encountered
	 * @throws IOException thrown if an IO error occurs  
	 */
	private void openSimulationAction() throws ParserConfigurationException, SAXException, IOException {
		JFileChooser fileBrowser = new JFileChooser();
		fileBrowser.setMultiSelectionEnabled(false);
		if (this.directory != null) {
			fileBrowser.setCurrentDirectory(this.directory);
		}
		int option = fileBrowser.showOpenDialog(this);
		// check the option
		if (option == JFileChooser.APPROVE_OPTION) {
			File file = fileBrowser.getSelectedFile();
			// make sure it exists and its a file
			if (file.exists() && file.isFile()) {
				this.directory = file.getParentFile();
				// read the file into a stream
				synchronized (this.world) {
					XmlReader.fromXml(file, this.world);					
				}
				// update the world tree
				this.pnlWorld.setWorld(this.world);
				// remove the current selection
				
			}
		}
	}
	
	/**
	 * Saves a snapshot of the simulation.
	 * @param auto true if the snapshot is an automatic snapshot
	 */
	private void takeSnapshot(boolean auto) {
		// get the xml for the current simulation
		String xml = "";
		synchronized (this.world) {
			xml = XmlGenerator.toXml(this.world);
		}
		// save it in the snapshot map using the timestamp as the key
		Date date = new Date();
		DateFormat df = new SimpleDateFormat("hh:mm:ss:SSS aa");
		String key = df.format(date);
		if (auto) {
			key = key + " (Automatic)";
		}
		// add it to the map
		this.snapshots.put(key, xml);
		
		JMenuItem mnuShot = new JMenuItem(key);
		mnuShot.setActionCommand("snapshotRestore");
		mnuShot.addActionListener(this);
		mnuShot.setIcon(Icons.SNAPSHOT);
		this.mnuSnapshot.add(mnuShot);
	}
	
	/**
	 * Restores the given snapshot.
	 * @param snapshot the snapshot key
	 * @throws ParserConfigurationException thrown if an error occurs in configuring the SAX parser
	 * @throws SAXException thrown if any parsing error is encountered
	 * @throws IOException thrown if an IO error occurs  
	 */
	private void restoreSnapshot(String snapshot) throws ParserConfigurationException, SAXException, IOException {
		String xml = this.snapshots.get(snapshot);
		// read the file into a stream
		synchronized (this.world) {
			XmlReader.fromXml(xml, this.world);
		}
		// update the world tree
		this.pnlWorld.setWorld(this.world);
	}
	
	/**
	 * Ends all actions (excluding the moveWorldAction).
	 */
	private void endAllActions() {
		this.selectBodyAction.end();
		this.editBodyAction.end();
		this.selectFixtureAction.end();
		this.moveBodyAction.end();
		this.rotateBodyAction.end();
		this.moveFixtureAction.end();
		this.rotateFixtureAction.end();
	}
	
	/**
	 * The main method; uses zero arguments in the args array.
	 * @param args the command line arguments
	 */
	public static final void main(String[] args) {
	    new Sandbox();
	}
}
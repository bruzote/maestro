package com.digero.maestro.view;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerListener;
import com.digero.common.midi.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;
import com.digero.common.view.NoteGraph;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartListener;
import com.digero.maestro.abc.LotroDrumInfo;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.NoteFilterSequencerWrapper;
import com.digero.maestro.midi.TrackInfo;
import com.digero.maestro.util.IDisposable;

@SuppressWarnings("serial")
public class DrumPanel extends JPanel implements IDisposable, TableLayoutConstants, ICompileConstants {
	//              0              1               2
	//   +--------------------+----------+--------------------+
	//   | TRACK NAME         | Drum     |  +--------------+  |
	// 0 |                    | +------+ |  | (note graph) |  |
	//   | Instrument(s)      | +-----v+ |  +--------------+  |
	//   +--------------------+----------+--------------------+
	private static final int HGAP = 4;
	private static final int TITLE_WIDTH = TrackPanel.TITLE_WIDTH_DRUMS - 78;
	private static final int COMBO_WIDTH = TrackPanel.SPINNER_WIDTH + 78;
	private static final double[] LAYOUT_COLS = new double[] {
			TITLE_WIDTH, COMBO_WIDTH, FILL
	};
	private static final double[] LAYOUT_ROWS = new double[] {
			0, PREFERRED, 0
	};

	private TrackInfo trackInfo;
	private NoteFilterSequencerWrapper seq;
	private AbcPart abcPart;
	private int drumId;

	private JCheckBox checkBox;
	private JComboBox drumComboBox;
	private DrumNoteGraph noteGraph;

	public DrumPanel(TrackInfo info, NoteFilterSequencerWrapper sequencer, AbcPart part, int drumNoteId) {
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

		this.trackInfo = info;
		this.seq = sequencer;
		this.abcPart = part;
		this.drumId = drumNoteId;

		TableLayout tableLayout = (TableLayout) getLayout();
		tableLayout.setHGap(HGAP);

		checkBox = new JCheckBox();
		checkBox.setSelected(abcPart.isDrumEnabled(trackInfo.getTrackNumber(), drumId));
		checkBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				abcPart.setDrumEnabled(trackInfo.getTrackNumber(), drumId, checkBox.isSelected());
			}
		});

		checkBox.setOpaque(false);

		String title = trackInfo.getTrackNumber() + ". " + trackInfo.getName();
		String instr;
		if (info.isDrumTrack())
			instr = MidiConstants.getDrumName(drumId);
		else {
			instr = Note.fromId(drumNoteId).abc;
			checkBox.setFont(checkBox.getFont().deriveFont(Font.BOLD));
		}

		checkBox.setToolTipText("<html><b>" + title + "</b><br>" + instr + "</html>");

		instr = Util.ellipsis(instr, TITLE_WIDTH, checkBox.getFont());
		checkBox.setText(instr);

		drumComboBox = new JComboBox(LotroDrumInfo.ALL_DRUMS.toArray());
		drumComboBox.setSelectedItem(getSelectedDrum());
		drumComboBox.setMaximumRowCount(20);
		drumComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				LotroDrumInfo selected = (LotroDrumInfo) drumComboBox.getSelectedItem();
				abcPart.setDrumMapping(trackInfo.getTrackNumber(), drumId, selected.note.id);
			}
		});

		seq.addChangeListener(sequencerListener);
		abcPart.addAbcListener(abcPartListener);

		noteGraph = new DrumNoteGraph(seq, trackInfo);
		noteGraph.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					seq.setNoteSolo(trackInfo.getTrackNumber(), drumId, true);
				}
			}

			public void mouseReleased(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					seq.setNoteSolo(trackInfo.getTrackNumber(), drumId, false);
				}
			}
		});

		addPropertyChangeListener("enabled", new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent evt) {
				updateState();
			}
		});

		add(checkBox, "0, 1");
		add(drumComboBox, "1, 1, f, c");
		add(noteGraph, "2, 1");

		updateState();
	}

	public void dispose() {
		noteGraph.dispose();
		abcPart.removeAbcListener(abcPartListener);
		seq.removeChangeListener(sequencerListener);
	}

	private AbcPartListener abcPartListener = new AbcPartListener() {
		public void abcPartChanged(AbcPartEvent e) {
			if (e.isPreviewRelated()) {
				checkBox.setEnabled(abcPart.isTrackEnabled(trackInfo.getTrackNumber()));
				checkBox.setSelected(abcPart.isDrumEnabled(trackInfo.getTrackNumber(), drumId));
				drumComboBox.setSelectedItem(getSelectedDrum());
				updateState();
			}
		}
	};

	private SequencerListener sequencerListener = new SequencerListener() {
		public void propertyChanged(SequencerEvent evt) {
			if (evt.getProperty() == SequencerProperty.TRACK_ACTIVE)
				updateState();
		}
	};

	private void updateState() {
		int trackNumber = trackInfo.getTrackNumber();
		boolean trackEnabled = abcPart.isTrackEnabled(trackNumber);

		checkBox.setEnabled(trackEnabled);
		drumComboBox.setEnabled(trackEnabled);
		drumComboBox.setVisible(abcPart.getInstrument() == LotroInstrument.DRUMS);

		if (!seq.isNoteActive(drumId)) {
			noteGraph.setNoteColor(ColorTable.NOTE_DRUM_OFF);
			noteGraph.setBadNoteColor(ColorTable.NOTE_DRUM_OFF);

			setBackground(ColorTable.GRAPH_BACKGROUND_OFF.get());
			checkBox.setForeground(ColorTable.PANEL_TEXT_OFF.get());
		}
		else if (trackEnabled && abcPart.isDrumEnabled(trackNumber, drumId)) {
			noteGraph.setNoteColor(ColorTable.NOTE_DRUM_ENABLED);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_ENABLED);

			setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED.get());
			checkBox.setForeground(ColorTable.PANEL_TEXT_ENABLED.get());
		}
		else {
			noteGraph.setNoteColor(ColorTable.NOTE_DRUM_DISABLED);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_DISABLED);

			setBackground(ColorTable.GRAPH_BACKGROUND_DISABLED.get());
			checkBox.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
		}

	}

	private LotroDrumInfo getSelectedDrum() {
		return LotroDrumInfo.getById(abcPart.getDrumMapping(trackInfo.getTrackNumber(), drumId));
	}

	private class DrumNoteGraph extends NoteGraph {
		public DrumNoteGraph(SequencerWrapper sequencer, TrackInfo trackInfo) {
			super(sequencer, trackInfo, -1, 1, 2, 5);
		}

		@Override
		protected int transposeNote(int noteId) {
			return 0;
		}

		@Override
		protected boolean isNotePlayable(int noteId) {
			return abcPart.isDrumPlayable(trackInfo.getTrackNumber(), drumId);
		}

		@Override
		protected boolean isNoteVisible(NoteEvent ne) {
			return ne.note.id == drumId;
		}
	}
}

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.UUID;

public class ApplicationWindow extends JFrame {

	public ApplicationWindow() {
		super("ZMods Patcher");

		this.setSize(200, 200);
		this.setResizable(false);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.getContentPane().setLayout(null);

		JButton buttonPatch = new JButton("Patch");
		buttonPatch.setBounds(30, 30, 140, 50);
		buttonPatch.setFont(new Font("Impact", Font.BOLD, 28));
		buttonPatch.addActionListener(new DoPatch(LabymodDomain.ORIGINAL, LabymodDomain.REPLACEMENT));
		this.getContentPane().add(buttonPatch);

		JButton buttonUnpatch = new JButton("Unpatch");
		buttonUnpatch.setBounds(30, 90, 140, 50);
		buttonUnpatch.setFont(new Font("Impact", Font.BOLD, 28));
		buttonUnpatch.addActionListener(new DoPatch(LabymodDomain.REPLACEMENT, LabymodDomain.ORIGINAL));
		this.getContentPane().add(buttonUnpatch);

		this.setVisible(true);
	}

	public static void main(String[] args) {
		new ApplicationWindow();
	}

	private class DoPatch implements ActionListener {
		private String toReplace;
		private String replaceWith;

		public DoPatch(String toReplace, String replaceWith) {
			this.toReplace = toReplace;
			this.replaceWith = replaceWith;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.setFileFilter(new FileNameExtensionFilter("Jar Files", "jar"));
			chooser.setDialogTitle("Select LabyMod Library File");
			int ret = chooser.showDialog(ApplicationWindow.this, "DAT BOI");
			if (ret == JFileChooser.APPROVE_OPTION) {
				File src = chooser.getSelectedFile();
				File dst = new File(src.getParent(), UUID.randomUUID().toString());
				boolean success = ASMStringReplacer.doPatch(src, dst, toReplace, replaceWith);
				if (success) {
					success = src.delete() && dst.renameTo(src);
				} else {
					dst.delete();
				}
				JOptionPane.showMessageDialog(ApplicationWindow.this, success ? "SUCCESS" : "ERROR");
			}
		}
	}
}

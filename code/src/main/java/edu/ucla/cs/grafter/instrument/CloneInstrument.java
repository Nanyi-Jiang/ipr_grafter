package edu.ucla.cs.grafter.instrument;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import javax.swing.JOptionPane;

import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.amazonaws.services.simpleemail.model.SetIdentityDkimEnabledRequest;
import com.thoughtworks.xstream.XStream;

import edu.ucla.cs.grafter.config.GrafterConfig;
import edu.ucla.cs.grafter.file.FileUtils;
import edu.ucla.cs.grafter.graft.analysis.CloneCalibrator;
import edu.ucla.cs.grafter.graft.analysis.CloneVisitor;

import static edu.ucla.cs.grafter.Constants.*;


public class CloneInstrument {
	// Whole path is /Users/jay/Grafter/code/src/main/resources/template/TestTracker.template
	static final String template = GRAFTER_CODE_PATH + "src/main/resources/template/TestTracker.template";
	int id;
	String path;
	int start;
	int end;
	private int start_new;
	private int end_new;

	public CloneInstrument(int id, String path, int start, int end) {
		this.id = id;
		this.path = path;
		this.start = start;
		this.end = end;
		this.start_new = start;
		this.end_new = end;
	}

	public static void showDiff(String filepath, int linenumber, String patch) {
		// CloneVisitor.parseSnipCode(filepath, linenumber);

		// get variables needed from CloneVisitor.parseSnipCode
		ArrayList<ArrayList<String>> vars;
		try {
			vars = CloneVisitor.parseSnipCode(filepath, linenumber);
		} catch (IOException e) {
			System.out.println("unable to perform CloneVisitor.parseSnipCode");
			return;
		}
		ArrayList<String> addedBefore = new ArrayList<>();
		ArrayList<String> addedAfter = new ArrayList<>();
		if (vars.size() == 0) {
			System.out.println("no variable used or defined");
			return;
		}

		String code;
		try {
			code = FileUtils.readFileToString(filepath);
		} catch (IOException e) {
			System.out.println("unable to perform FileUtils.readFileToString");
			return;
		}

		String lineSeparator = System.getProperty("line.separator");
		String[] cc = code.split(lineSeparator);
		String before = "";
		String after = "";
		// add import statement
		int lineToInsertImport = 0;
		int inComment = 0;
		for (int i = 0; i < cc.length; i++) {
			if (cc[i].contains("/*")) {
				inComment = 1;
			}
			if (cc[i].contains("*/")) {
				inComment = 0;
			}
			// if in comment, we want to skip this line
			if (inComment == 1) {
				continue;
			}

			if (cc[i].contains("package") || cc[i].contains("import")) {
				lineToInsertImport = i;
			}
		}
		if (lineToInsertImport != 0) {
			lineToInsertImport++;
		}
		cc[lineToInsertImport] = "import com.thoughtworks.xstream.XStream;" + lineSeparator + cc[lineToInsertImport];
		// add before and after
		for (int i = 0; i < cc.length; i++) {
			if (i < linenumber - 1) {
				before += cc[i] + lineSeparator;
			} else if (i > linenumber - 1) {
				after += cc[i] + lineSeparator;
			}
		}

		// add sentenses to use XStream
		ArrayList<String> serialSentenses = addSerialization();
		for (String each : serialSentenses) {
			addedBefore.add(each);
		}

		// create the inserted lines
		// vars[0] is used variable list; vars[1] is defined variable list
		for (String each : vars.get(0)) {
			addedBefore.add("System.out.println(\"before line " + linenumber + ", "
					+ each + " is " + "\"+ " + "xtream.toXML(" + each + ")" + ");");
			addedAfter.add("System.out.println(\"after line " + linenumber + ", "
					+ each + " is " + "\"+ " + "xtream.toXML(" + each + ")" + ");");
		}
		for (String each : vars.get(1)) {
			addedAfter.add("System.out.println(\"after line " + linenumber + ", "
					+ each + " is " + "\"+ " + "xtream.toXML(" + each + ")" + ");");
		}

		// change the name of the original file
		File backup = new File(filepath + ".bak");
		File old_file = new File(filepath);
		boolean ifRename = old_file.renameTo(backup);
		if (ifRename) {
			System.out.println("rename sucess");
		}

		// create a new file with the same name
		File new_file = new File(filepath);
		// testfilepath is the location that our new clone files are located
		try {
			// make sure we have another folder for these altered java files
			new_file.createNewFile();
		} catch (IOException e) {
			System.out.println("unable to perform createNewFile");
			e.printStackTrace();
			return;
		}

		code = before;
		for (String each : addedBefore) {
			code += each + lineSeparator;
		}
		// code += cc[linenumber - 1] + lineSeparator;
		// instead of using the original line, we want to replace it with a patch
		code += patch + lineSeparator;
		for (String each : addedAfter) {
			code += each + lineSeparator;
		}
		code += after;
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(new_file));
			bw.write(code);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (bw != null)
				try {
					bw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		// we should run this new clone file
		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.command(GRAFTER_CODE_PATH + "myscript.sh", "CloneInstrumentTest");

		Process process;
		try {
			process = processBuilder.start();
		} catch (IOException e) {
			e.printStackTrace();
			File delete = new File(filepath);
			File originalPath = new File(filepath);
			if (delete.exists()) {
				delete.delete();
				File old = new File(filepath + ".bak");
				ifRename = old.renameTo(originalPath);
				if (ifRename) {
					System.out.println("rename(back) sucess");
				}
			}
			return;
		}

		try {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					System.out.println(line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// after we run the new file, we need to delete this clone file and rename the
		// old file back

		File delete = new File(filepath);
		File originalPath = new File(filepath);
		if (delete.exists()) {
			delete.delete();
			File old = new File(filepath + ".bak");
			ifRename = old.renameTo(originalPath);
			if (ifRename) {
				System.out.println("rename(back) sucess");
			}
		}

	}

	// addSerialization() returns a list of sentenses that are required for using
	// XStream
	private static ArrayList<String> addSerialization() {
		ArrayList<String> sentenses = new ArrayList<>();
		sentenses.add("XStream xstream = new XStream();");
		return sentenses;
	}

	public void instrument() throws InstrumentException {
		try {
			addTestTracker();
			updateRange();
			insertPrintStatement();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	void addTestTracker() throws IOException {
		// Check if TestTracker.java exists in the current package
		String dir = path.substring(0, path.lastIndexOf(File.separator));
		String testTracker = dir + File.separator + "TestTracker.java";
		File file = new File(testTracker);

		if (!file.exists()) {
			// Get the package name
			String packageName = getPackageName();
			// Customize the template
			String code = "package " + packageName + ";" + System.lineSeparator()
					+ FileUtils.readFileToString(template);

			// Create TestTracker.java in the package of the code clone
			file.createNewFile();
			BufferedWriter bw = null;
			try {
				bw = new BufferedWriter(new FileWriter(file));
				bw.write(code);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (bw != null)
					bw.close();
			}
		}
	}

	/**
	 * Update the start and end line numbers, since Grafter may have inserted print
	 * statements into the same file for other clone before
	 *
	 * @throws IOException
	 * @throws InstrumentException
	 */
	void updateRange() throws IOException, InstrumentException {
		String code = FileUtils.readFileToString(this.path);
		String lineSeparator = System.getProperty("line.separator");

		String[] cc = code.split(lineSeparator);
		int counter = 0; // count the inserted print statements before the clone
		int counter2 = 0; // count the inserted print statements in the clone
		for (int i = 0; i < cc.length; i++) {
			if (i < this.start && cc[i].contains("TestTracker.getTestName")) {
				counter++;
			} else if (i >= this.start && i <= this.end && cc[i].contains("TestTracker.getTestName")) {
				// a clone may appear in other clone groups and therefore can be instrumented
				// before
				counter2++;
			}
		}

		this.start_new += counter;
		this.end_new += counter + counter2;
	}

	void insertPrintStatement() throws IOException, InstrumentException {
		// Get the line number of the first statement in the clone
		CompilationUnit cu = JavaParser.parse(this.path);
		CloneCalibrator calibrator = new CloneCalibrator(cu, this.start_new, this.end_new);
		cu.accept(calibrator);

		if (calibrator.first == Integer.MAX_VALUE) {
			// we cannot find the first statment in the clone, please double check the
			// validity of the clone
			if (GrafterConfig.batch) {
				System.out.println("[Grafter]Cannot find the first statement in the clone in file -- " + this.path
						+ " -- in group " + this.id);
			} else {
				JOptionPane.showMessageDialog(null, "[Grafter]Cannot find the first statement in the clone in file -- "
						+ this.path + " -- in group " + this.id);
			}

			throw new InstrumentException();
		}

		// insert the print statement in the clone
		int ln = calibrator.first;
		String clazz = this.path.substring(this.path.lastIndexOf(File.separator) + 1, this.path.lastIndexOf('.'));
		String instr = "System.out.println(\"[Grafter][Clone Group " + this.id + "][Class " + clazz + "][Range("
				+ this.start + "," + this.end + ")]\"+ TestTracker.getTestName());";
		String code = FileUtils.readFileToString(this.path);
		String lineSeparator = System.getProperty("line.separator");

		String[] cc = code.split(lineSeparator);
		String before = "";
		String after = "";
		for (int i = 0; i < cc.length; i++) {
			if (i + 1 < ln) {
				before += cc[i] + lineSeparator;
			} else {
				after += cc[i] + lineSeparator;
			}
		}

		// rename the old file
		File backup = new File(this.path + ".bak");
		if (!backup.exists()) {
			// no need to back up multiple times
			File old_file = new File(this.path);
			backup.createNewFile();
			old_file.renameTo(new File(this.path + ".bak"));
		}

		// create a new file with the same name
		File new_file = new File(this.path);
		new_file.createNewFile();
		code = before + instr + lineSeparator + after;
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new FileWriter(new_file));
			bw.write(code);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (bw != null)
				bw.close();
		}
	}

	String getPackageName() {
		try {
			CompilationUnit cu = JavaParser.parse(path);
			return cu.getPackage().getName().getFullyQualifiedName();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return "";
	}

	// used for manual testing
	public static void main(String[] args) {
		CloneInstrument.showDiff(
				GRAFTER_DATASET_PATH + "ant/src/main/org/apache/tools/ant/types/PatternSet.java",
				343, "StringTokenizer tok = new StringTokenizer(includes, \", \", false);");
	}
}

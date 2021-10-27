package sqlfe.junit;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import org.apache.commons.io.*;

import org.junit.jupiter.api.Test;

import javafx.embed.swing.JFXPanel;
import sqlfe.general.BackEnd;
import sqlfe.general.FrontEnd;

class BackEndTests extends AbstractTest {

	@Test
	void test() {
		try {
			// initialize jfx for test to run
			JFXPanel fxPanel = new JFXPanel();
			
			// set default values for testing an input
			// values will need to be changed for information used in personal testing database
			String dbmsChoice = "MySQL 8.0";
			String dbmsHost = "localhost";
			String dbmsPort = "3306";
			String dbmsSystemID = "evaltest";
			String dbmsUsername = "root";
			String dbmsPassword = "pass";
			String evaluationFolder = "C:\\Users\\andrewshipman\\eclipse-workspace\\SQLFE_project";
			String assignPropFile = "assignmentProperties-MySQL";
			FrontEnd f = new FrontEnd();
			
			// set values for the front end used for testing
			f.setDbmsChoice(dbmsChoice);
			f.setDbmsHost(dbmsHost);
			f.setDbmsPort(dbmsPort);
			f.setDbmsSystemID(dbmsSystemID);
			f.setDbmsUsername(dbmsUsername);
			f.setDbmsPassword(dbmsPassword);
			f.setEvaluationFolder(evaluationFolder);
			f.setAssignPropFile(assignPropFile);
			
			// setup back end and run evaluation
			BackEnd b = new BackEnd(f);
			f.setABackEnd(b);
			b.transferData(f);
			b.evaluate();
			
			
			// test if output file was created
			File testFile = new File(f.getEvaluationFolder() + "\\evaluations\\AAA_grade_summary.out");
			assertTrue(testFile.exists());
			
			// test if there is the proper number of files
			File inputFiles = new File(f.getEvaluationFolder() + "\\files");
			File outputFiles = new File(f.getEvaluationFolder() + "\\Evaluations");
			assertEquals(inputFiles.list().length, (outputFiles.list().length - 3));
			
			
			// test if grade summary output is correct for 5 files
			// uses file in testing file to assert output is correct for 5 evaluations
			File compareFile = new File(f.getEvaluationFolder() + "\\TestingFiles\\Correct_5_evals_summary.out");
			assertTrue(FileUtils.contentEquals(testFile, compareFile));
			
			
		}catch(Exception e) {
			System.out.println(e);
            fail();
		}
	}

}

import java.io.*;
import javax.microedition.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class BlueTerm extends MIDlet implements CommandListener, Runnable {
	private TelnetCanvas canvas;
	private StreamConnection conn;
	private InputStream input;
	private OutputStream output;
	private Command killCommand, sttyCommand, escCommand, nonlCommand, clearCommand, scrollCommand, runCommand, exitCommand, okCommand;
	private TextBox msg;

	public void startApp() {
		conn = null;

		okCommand = new Command("Send", Command.OK, 0);
		escCommand = new Command("Send as Escape", Command.OK, 0);
		nonlCommand = new Command("Send without Newline", Command.OK, 0);
		clearCommand = new Command("Clear", Command.OK, 0);
		killCommand = new Command("Send SIGINT", Command.OK, 0);
		scrollCommand = new Command("Toggle Scroll", Command.OK, 0);
		runCommand = new Command("Send Input", Command.OK, 0);
		sttyCommand = new Command("Send stty rows cols", Command.OK, 0);
		exitCommand = new Command("Exit", Command.EXIT, 0);

		msg = new TextBox("", "", 100, TextField.ANY);
		msg.addCommand(okCommand);
		msg.addCommand(escCommand);
		msg.addCommand(nonlCommand);
		msg.addCommand(clearCommand);

		canvas = new TelnetCanvas(Display.getDisplay(this));
		canvas.setup();

		canvas.addCommand(runCommand);
		canvas.addCommand(killCommand);
		canvas.addCommand(scrollCommand);
		canvas.addCommand(sttyCommand);
		canvas.addCommand(exitCommand);

		canvas.setCommandListener(this);
		Display.getDisplay(this).setCurrent(canvas);

		canvas.receive("BlueTerm " + getAppProperty("MIDlet-Version") + "\n\nConnecting to " + getAppProperty("BT-Address") + "...\n\n");

		new Thread(this).start(); // Bluetooth thread
	}

	public void sendBuffer(byte buffer) {
		try {
			output.write(buffer);
			output.flush();
		} catch (IOException e) {
			canvas.receive("Send Failed: " + e.getMessage() + "\n");
		}
	}

	public void sendBuffer(byte[] bytes, boolean printNewline) {
		try {
			output.write(bytes);
			if (printNewline) output.write("\n".getBytes());
			output.flush();
		} catch (IOException e) {
			canvas.receive("Send Failed: " + e.getMessage() + "\n");
		}
	}

	public void commandAction(Command cmd, Displayable disp) {
		if (cmd == killCommand) {
			sendBuffer((byte)0x03);
		} else if (cmd == runCommand) {
			msg.setCommandListener(this);
			Display.getDisplay(this).setCurrent(msg);
		} else if (cmd == nonlCommand) {
			sendBuffer(msg.getString().getBytes(), false);
			canvas.setCommandListener(this);
			Display.getDisplay(this).setCurrent(canvas);
		} else if (cmd == escCommand) {
			sendBuffer((byte)(msg.getString().toUpperCase().charAt(0)-64));
			canvas.setCommandListener(this);
			Display.getDisplay(this).setCurrent(canvas);
		} else if (cmd == clearCommand) {
			msg.setString("");
		} else if (cmd == okCommand) {
			sendBuffer(msg.getString().getBytes(), true);
			canvas.setCommandListener(this);
			Display.getDisplay(this).setCurrent(canvas);
		} else if (cmd == scrollCommand) {
			canvas.setScrolling(!canvas.isScrolling()); // Toggle Scrolling
		} else if (cmd == sttyCommand) {
			sendBuffer(("stty rows " + canvas.getRows() + " cols " + canvas.getColumns()).getBytes(), true);
		} else if (cmd == exitCommand) {
			try {
				conn.close();
			} catch (IOException e) {
				canvas.receive("Disconnect Failed: " + e.getMessage() + "\n");
			}
			destroyApp(false);
		}
	}

	public void run() {
		try {
			conn = (StreamConnection)Connector.open(getAppProperty("BT-Address"));
		} catch (IOException e) {
			canvas.receive("Connection Failed: " + e.getMessage() + "\n");
			return;
		}
		try {
			byte buffer[] = new byte[100];
			input = conn.openInputStream();
			output = conn.openOutputStream();
			canvas.setOutputStream(output);
			int c;
			while ((c = input.read()) != -1) {
				canvas.receive((byte)c);
			}
			canvas.receive("\nDisconnected.\n");
		} catch (IOException e) {
			canvas.receive("Receive Failed: " + e.getMessage() + "\n");
		}
	}

	protected void destroyApp(boolean unconditional) {
		notifyDestroyed();
	}

	protected void pauseApp() {
	}
}

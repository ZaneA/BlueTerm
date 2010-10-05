BlueTerm 1.0.0
==============

Really basic bluetooth terminal for J2ME devices (tested only on Nokia)


Compiling
---------
Take a look at src/compile.sh and adjust as neccesary.

Linux Setup:
-----------

- Create a file named login.sh with the following contents and run "chmod +x login.sh" to make it executable (Modify as appropriate):

    \#!/bin/sh
    exec sudo agetty -8 38400 rfcomm0 ansi

- Create a file named bluetoothio.sh with the following contents and run "chmod +x bluetoothio.sh" to make it executable (Modify as appropriate):

    \#!/bin/sh
    exec sudo rfcomm watch /dev/rfcomm0 1 $1

- Now run with "./bluetoothio.sh ./login.sh". It should now be awaiting an rfcomm connection on channel 1, when one is active login.sh will be launched to handle this.
- Modify BT-Address inside BlueTerm.jad to point to your bluetooth device id (you can find this with "hcitool dev").

Phone Setup:
-----------

- Copy BlueTerm.jad and BlueTerm.jar to your phone.
- Run and accept the bluetooth connection warning if one appears.
- BlueTerm should now be connected and you should see a login prompt, pressing OK should give you a menu in which to send input. Read the source for details.


Enjoy!

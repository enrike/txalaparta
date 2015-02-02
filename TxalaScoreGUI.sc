// license GPL
// by www.ixi-audio.net

/*
t = TxalaScoreGUI.new
to do: improve detected amplitude range and values
*/

TxalaScoreGUI{

	var parent;
	var txalascoreevents, txalascore, timelinewin, txalascoreF, txalascoresttime;

	*new {
		^super.new.initTxalaScoreGUI();
	}

	initTxalaScoreGUI {
		this.reset()
	}

	reset {
		txalascoreF = Routine({
			inf.do({
				var now = Main.elapsedTime - txalascoresttime;
				txalascore.update(txalascoreevents, now);
				0.05.wait;
			});
		});
	}


	hit { arg hittime, amp, player, freq;
		var hitdata, plank;
		if (txalascore.isNil.not, {
			hittime = hittime - txalascoresttime;
			plank = 1; // TO DO: match freq to a sample
			//[hittime, amp, player, plank].postln;
			hitdata = ().add(\time -> hittime)
			            .add(\amp -> amp)
					    .add(\player -> player) //always 1 in this case
					    .add(\plank -> plank);// here needs to match mgs[5] against existing samples freq
			txalascoreevents = txalascoreevents.add(hitdata)
		});
	}

	doTxalaScore { arg xloc=0, yloc=600, width=1020, height=350, timeframe=4;
		var view, xstep=0, drawspeed=1, numactiveplanks=0;
		if (timelinewin.isNil, {
			timelinewin = Window("Timeline", Rect(xloc, yloc, width, height));

			numactiveplanks = 1;//txalaparta.getnumactiveplanks();

		    txalascoresttime = Main.elapsedTime;

			txalascore = TxalaScore.new(timelinewin,
				Rect(0, 0, timelinewin.bounds.width, timelinewin.bounds.height-25),
				numactiveplanks);

			txalascore.timeframe = timeframe;
			//tscore.recordScore = true;

			EZSlider( timelinewin,         // parent
				Rect(-40,timelinewin.bounds.height-22,200,20),    // bounds
				"zoom",  // label
				ControlSpec(20, 1, \lin, 0.001, 10, "ms"),     // controlSpec
				{ arg ez;
					txalascore.timeframe = ez.value;
				},
				initVal: timeframe,
				labelWidth: 80;
			);

			Button(timelinewin, Rect(200,timelinewin.bounds.height-22,75,20))
			.states_([
				["save score", Color.white, Color.black]
			])
			.action_({ arg butt;
				"this should save the score to a MIDI file".postln;
			});

			AppClock.play(txalascoreF);

			timelinewin.onClose = {timelinewin=nil}; // only one instance please
			timelinewin.front;
			//timelinewin.alwaysOnTop = true;
		});
	}
}
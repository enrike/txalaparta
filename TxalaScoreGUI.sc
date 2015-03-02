// license GPL
// by www.ixi-audio.net

/*
t = TxalaScoreGUI.new
t.doTxalaScore()
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
		txalascoreevents = nil;
		if (txalascore.isNil.not, {txalascore.events = nil});
		txalascoreF = Routine({
			inf.do({
				if (txalascore.isNil.not, {
					var now = Main.elapsedTime - txalascoresttime;
					txalascore.update(txalascoreevents, now);
				});
				0.05.wait;
			});
		});
	}


	hit { arg hittime, amp, player, freq;
		var hitdata, plank;
		if (txalascore.isNil.not, {
			hittime = hittime - txalascoresttime;
			plank = freq; // TO DO: match freq to a sample
			//[hittime, amp, player, plank].postln;
			hitdata = ().add(\time -> hittime)
			            .add(\amp -> amp)
					    .add(\player -> player) //always 1 in this case
					    .add(\plank -> plank);// here needs to match mgs[5] against existing samples freq
			txalascoreevents = txalascoreevents.add(hitdata)
		});
	}


	close {
		timelinewin.close()
	}

	updateNumPlanks { arg numplanks;
		// DO NOT UPDATE IF MODE 0?
		var mode = 0;
		if (txalascore.isNil.not, { mode = txalascore.drawmode});
		txalascore = nil;
		if ( (timelinewin.isNil.not), {
			txalascore = TxalaScore.new(timelinewin,
				Rect(0, 0, timelinewin.bounds.width, timelinewin.bounds.height-25), numplanks, mode)
		})
	}

	doTxalaScore { arg xloc=0, yloc=600, width=1020, height=350, timeframe=4, numactiveplanks=1;
		var view, xstep=0, drawspeed=1;
		if (timelinewin.isNil, {
			timelinewin = Window("Timeline", Rect(xloc, yloc, width, height));

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
				var midifile;
				try {
					midifile = SimpleMIDIFile( "~/" ++ Date.getDate.stamp ++ ".mid" ); // create empty file
					//m.addNote ( noteNumber, velo, startTime, dur, upVelo, channel, track:1, sort )
					txalascoreevents.do({arg evt;
						midifile.addNote(
							noteNumber: evt.plank,
							velo: evt.amp * 127,
							startTime: evt.time,
							dur: 0.2,
							channel: evt.player
						)
					});
					~mififile=midifile;
					midifile.plot;
					midifile.write;
				} {|error|
					["no wslib Quarks installed in your system?", error].postln;
				};
			});

			Button(timelinewin, Rect(280,timelinewin.bounds.height-22,75,20))
			.states_([
				["mode", Color.white, Color.black],
				["mode", Color.white, Color.green]
			])
			.action_({ arg butt;
				//txalascore.drawmode = butt.value.asInt;
				// here we need to update the num of planks in the case we are back to mode 1

				var numplanks = txalascore.numplanks;
				txalascore = TxalaScore.new(timelinewin,
					Rect(0, 0, timelinewin.bounds.width, timelinewin.bounds.height-25), numplanks, butt.value.asInt)
				//txalascore.setdrawmode(butt.value.asInt);
			});

			AppClock.play(txalascoreF);

			timelinewin.onClose = {timelinewin=nil}; // only one instance please
			timelinewin.front;
			//timelinewin.alwaysOnTop = true;
		});
	}
}
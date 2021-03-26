// license GPL
// by www.ixi-audio.net




/*
usage:

n = 3;
w = TxalaScoreGUI.new;
w.doTxalaScore();
w.updateNumPlanks(n);
p = true;

//w.mark(tempocalc.lasttime, SystemClock.seconds, txalasilence.compass, lastPattern.size)


fork{
	inf.do({arg i;
		p = p.not;
		w.hit(SystemClock.seconds, rrand(0.2, 1), p.asInt, n.rand); // time, amp, player, plank
		0.2.wait;
	});
};
w.close
*/

TxalaScoreGUI{

	var parent;
	var txalascoreevents, txalascoremarks, txalascore, timelinewin, txalascoreF, txalascoresttime;

	*new {
		^super.new.initTxalaScoreGUI();
	}

	initTxalaScoreGUI {
		this.reset()
	}

	reset {
		txalascoreevents = nil;
		txalascoremarks = nil;
		if (txalascore.isNil.not, {txalascore.events = nil; txalascore.marks = nil});
		txalascoreF = Routine({ // is a Routine the best way?
			inf.do({
				var frame = 40;
				if (txalascore.isNil.not, {
					txalascore.update( Main.elapsedTime - txalascoresttime );
				});
				0.05.wait; // 20fps. 1/20
			});
		});
	}


	hit { arg hittime, amp, player, freq=nil, stick=nil;
		var hitdata, plank;
		if (txalascore.isNil.not, {
			hittime = hittime - txalascoresttime;
			plank = freq; // TO DO: match freq to a sample
			hitdata = ().add(\time -> hittime)
			            .add(\amp -> amp)
					    .add(\player -> player) //always 1 in this case
					    .add(\plank -> plank)// here needs to match mgs[5] against existing samples freq
					    .add(\stick -> stick);
			txalascoreevents = txalascoreevents.add(hitdata);
			txalascore.events = txalascoreevents;
		});
	}

	mark {arg sttime, endtime, compassnum, hitnum; // patterns time is relative to first hit. starts with 0
		var data;
		if (txalascore.isNil.not, {
			data = ()
			.add(\start -> (sttime - txalascoresttime))
			.add(\end-> (endtime - txalascoresttime))
			.add(\num-> compassnum)
			.add(\hits-> hitnum);
			txalascoremarks = txalascoremarks.add(data);
			txalascore.marks = txalascoremarks;
		});
	}

	close {
		if (timelinewin.isNil.not, {
			timelinewin.close();
			timelinewin = nil;
		});
	}

	updateNumPlanks { arg numplanks;
		// DO NOT UPDATE IF MODE 0?
		var mode = 0, tframe, group, planks;
		if (txalascore.isNil.not, {
			mode = txalascore.drawmode;
			tframe = txalascore.timeframe;
			group = txalascore.drawgroup;
			planks = txalascore.drawplanks;
		} );
		txalascore = nil;
		if ( (timelinewin.isNil.not), {
			txalascore = TxalaScore.new(timelinewin,
				Rect(0, 0, timelinewin.bounds.width, timelinewin.bounds.height-25), numplanks, tframe, mode, planks, group)
		})
	}

	doTxalaScore { arg xloc=0, yloc=400, width=1020, height=350, timeframe=4, numactiveplanks=1;
		var view, xstep=0, drawspeed=1;
		if (timelinewin.isNil, {
			timelinewin = Window(~txl.do("Timeline"), Rect(xloc, yloc, width, height));

		    txalascoresttime = Main.elapsedTime;
			txalascore = nil;
			txalascore = TxalaScore.new(timelinewin,
				Rect(0, 0, timelinewin.bounds.width, timelinewin.bounds.height-25),
				numactiveplanks);

			txalascore.timeframe = timeframe;
			//tscore.recordScore = true;

			EZSlider( timelinewin,         // parent
				Rect(-40,timelinewin.bounds.height-22,200,20),    // bounds
				~txl.do("zoom"),  // label
				ControlSpec(20, 1, \lin, 0.001, 10, "ms"),     // controlSpec
				{ arg ez;
					txalascore.timeframe = ez.value;
				},
				initVal: timeframe,
				labelWidth: 80;
			);

			Button(timelinewin, Rect(200,timelinewin.bounds.height-22,75,20))
			.states_([
				[~txl.do("save MIDI"), Color.white, Color.black]
			])
			.action_({ arg butt;
				var midifile;
				try {
					midifile = SimpleMIDIFile( Platform.userHomeDir ++ "/" ++ Date.getDate.stamp ++ ".mid" ); // create empty file
					midifile.init1( 2, 120, "4/4" );
					//m.addNote ( noteNumber, velo, startTime, dur, upVelo, channel, track:1, sort )
					("trying to save midi file at"+Platform.userHomeDir).postln;
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
				[~txl.do("mode"), Color.white, Color.black],
					[~txl.do("mode"), Color.white, Color.green]
			])
			.action_({ arg butt;
				var group, tframe, planks;
				group = txalascore.drawgroup;// remember past state
				planks = txalascore.drawplanks;
				tframe = txalascore.timeframe;
				txalascore = TxalaScore.new(timelinewin,
					Rect(0, 0, timelinewin.bounds.width, timelinewin.bounds.height-25),
					txalascore.numplanks, tframe, butt.value.asBoolean, planks, group);
				txalascoreevents = nil; // now clear local event memory
				txalascoremarks = nil
			}).valueAction_(1);

			Button(timelinewin, Rect(360,timelinewin.bounds.height-22,75,20))
			.states_([
				[~txl.do("planks"), Color.white, Color.black],
				[~txl.do("planks"), Color.white, Color.green]
			])
			.action_({ arg butt;
				txalascore.drawplanks = butt.value.asBoolean;
			}).valueAction_(1);

			Button(timelinewin, Rect(440,timelinewin.bounds.height-22,75,20))
			.states_([
				[~txl.do("draw group"), Color.white, Color.black],
					[~txl.do("draw group"), Color.white, Color.green]
			])
			.action_({ arg butt;
				txalascore.drawgroup = butt.value.asBoolean;
			}).valueAction_(1);

			AppClock.play(txalascoreF);

			timelinewin.onClose = {timelinewin=nil}; // only one instance please
			timelinewin.front;
			//timelinewin.alwaysOnTop = true;
		});
	}
}
// license GPL
// by www.ixi-audio.net

/*
t = TxalaSilenceDetection.new(this, s, true)
(not implemented yet) second argument is mode. 0 audioin, 1 MIDI in, 2 OSC in
third argument is answer mode. it sets the answer schedule time to groupdetect or groupend events
*/

TxalaSilenceDetection{

	var server, parent, <>compass, hitflag, >hutsunetimeout, groupst;
	var resettime, <>answerposition, silencedefOSC;
	var <synth;

	*new {| aparent, aserver, ananswerposition = true |
		^super.new.initTxalaSilenceDetection(aparent, aserver, ananswerposition);
	}

	initTxalaSilenceDetection { arg aparent, aserver, ananswerposition;
		parent = aparent;
		server = aserver;
		answerposition = ananswerposition;
		this.reset()
	}

	reset {
		compass = 0;
		hutsunetimeout = nil;
		hitflag = false;
		resettime = 5; // how many secs to wait before reseting the system. TO DO: useful to expose in the GUI?
		groupst = 0;

		this.doAudio();
	}

	kill {
		synth.free;
		synth = nil;
		silencedefOSC.clear;
		silencedefOSC.free;
	}

	doAudio {
		this.kill(); // force

		SynthDef(\txalatempo, {| in=0, gain=1, threshold=0.45, falltime=0.15, checkrate=20, comp_thres=0.3 |
			var detected, signal;
			signal = SoundIn.ar(in)*gain;
			signal = HPF.ar(signal, 180); // kill long low freqs in long planks
			signal = Compander.ar(signal, signal, // expand loud sounds and get rid of low ones
				thresh: comp_thres,// THIS IS CRUCIAL. in RMS
				slopeBelow: 1.9, // almost noise gate
				slopeAbove: 1.8, // >1 to get expansion
				clampTime: 0.005,
				relaxTime: 0.01
			);
			detected = DetectSilence.ar( signal, amp:threshold, time:falltime );
			SendReply.kr(Impulse.kr(checkrate), '/txalasil', detected); // collect somewhere else
			Out.ar(3, signal); // for monitoring
		}).add;

		{
			synth = Synth(\txalatempo, [
				\in, ~listenparemeters.in,
				\gain, ~listenparemeters.gain,
				\threshold, ~listenparemeters.tempo.threshold,
				\falltime, ~listenparemeters.tempo.falltime,
				\checktime, ~listenparemeters.tempo.checkrate,
				\comp_thres, ~listenparemeters.tempo.comp_thres,
			]);
		}.defer(0.5);// to make sure the SynthDef is ready to instantiate

		silencedefOSC = OSCdef(\txalasilenceOSCdef, {|msg, time, addr, recvPort| this.process(msg[3])}, '/txalasil', server.addr);
	}

	updatethreshold {arg value; // this is because DetectSilence cannot be updated on realtime :(
		~listenparemeters.tempo.threshold = value;
		synth.set(\threshold, value);
		//this.updatesynth();
	}

	updatefalltime {arg value; // this is because DetectSilence cannot be updated on realtime :(
		~listenparemeters.tempo.falltime = value;
		synth.set(\falltime, value);
		//this.updatesynth();
	}


/*	updatesynth {
		// supercollider does not allow to update the DetectSilence's amp parameter on the fly
		// so we need to kill and instantiate the synth again and again. Only on slider mouseUP, otherwise we get into troubble
		// because sometimes too many instances stay in the server memory causing mess
		synth.free;
		synth = nil;
		{
			synth = Synth(\txalatempo, [
				\in, ~listenparemeters.in,
				\amp, ~listenparemeters.amp,
				\threshold, ~listenparemeters.tempo.threshold,
				\falltime, ~listenparemeters.tempo.falltime,
				\checktime, ~listenparemeters.tempo.checkrate,
			])
		}.defer(0.2);// to make sure the SynthDef is ready to instantiate?
	}*/

	groupstart {
		groupst = SystemClock.seconds;
		parent.broadcastgroupstarted();
		//if ( (~hutsunelookup > 0) && (compass > 2), {
		if ( (~hutsunelookup == 1) && (compass > 2), {
			// TO DO: fix false positives
			hutsunetimeout = groupst + (60/~bpm) + ((60/~bpm) * 0.45);//* ~hutsunelookup); // next expected hit should happen before hutsunetimeout
		});
		hitflag = true;
		compass = compass + 1;
	}

	// scheduling answers at this moment does not work with fast tempos as the tail of the signal steps
	// over the answer time -> there is no silence between groups or silence is too short.
	groupend {
		hitflag = false;
		parent.broadcastgroupended(); // needed by onset detector to close pattern groups. should this be called from here or from onset detection??
	}

	// checks for empty phases in the compass
	checkhutsune {
		if (SystemClock.seconds >= hutsunetimeout, {
			parent.hutsune(); // need to update it was 0 hits
			hutsunetimeout = nil;
			compass = compass + 1; // advance manually
		})
	}

	// if too long after the last signal we received reset me
	checkreset {
		if ((SystemClock.seconds > (groupst + resettime)), {
			parent.reset();
			"RESET".postln;
		});
	}

	// loop for automatic txalaparta that listens to DetectSilence. triggered from an OSCFunc
	// calculates tempo and schedules answer in time with tempo. it tries to find out hutsunes
	// and resets the system if no input for longer than resettime secs
	process {arg value;
		//if (~listening, {
			if (value == 0, { // there is signal
				if (hitflag.not, { this.groupstart() }) // a new group of hits just started
			}, { // there is silence
				if (hitflag, {
					this.groupend();
				}, {
					if ( hutsunetimeout.isNil.not, {
						this.checkhutsune();
						//}, {
						//	this.checkreset();
					});
				});
			});
		//})
	}
}
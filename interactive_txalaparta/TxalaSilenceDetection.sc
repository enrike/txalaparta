// license GPL
// by www.ixi-audio.net

/*
t = TxalaSilenceDetection.new(this, s, true)
(not implemented yet) second argument is mode. 0 audioin, 1 MIDI in, 2 OSC in
third argument is answer mode. it sets the answer schedule time to groupdetect or groupend events
*/

TxalaSilenceDetection{

	var server, parent, <>compass, hitflag, hutsunetimeout, groupst;
	var  resettime, <>answerposition; //>processflag,
	var synthOSCcb, <synth;

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
		//processflag = false;
		hitflag = false;
		resettime = 5; // how many secs to wait before reseting the system
		groupst = 0;

		this.doAudio();
	}

	kill {
		synth.free;
		synth = nil;
		OSCdef(\txalasilenceOSCdef).clear;
		OSCdef(\txalasilenceOSCdef).free;
	}

	doAudio {
		this.kill(); // force

		SynthDef(\txalatempo, {| in=0, gain=1, threshold=0.5, falltime=0.1, checkrate=20 | //thres=0,1
			var detected;
			detected = DetectSilence.ar( SoundIn.ar(in)*gain, amp:threshold, time:falltime );
			SendReply.kr(Impulse.kr(checkrate), '/txalasil', detected); // collect somewhere else
		}).add;

		{
			synth = Synth(\txalatempo, [
				\in, ~listenparemeters.in,
				\gain, ~listenparemeters.gain,
				\threshold, ~listenparemeters.tempo.threshold,
				\falltime, ~listenparemeters.tempo.falltime,
				\checktime, ~listenparemeters.tempo.checkrate,
			]);
		}.defer(0.5);// to make sure the SynthDef is ready to instantiate

		OSCdef(\txalasilenceOSCdef, {|msg, time, addr, recvPort| this.process(msg[3])}, '/txalasil', server.addr);
	}

	updatethreshold { arg value;
		// supercollider does not allow to update the DetectSilence's amp parameter on the fly
		// so we need to kill and instantiate the synth again and again. Only on slider mouseUP, otherwise we get into troubble
		// because sometimes too many instances stay in the server memory causing mess
		synth.free;
		synth = nil;
		{
			synth = Synth(\txalatempo, [
				\in, ~listenparemeters.in,
				\amp, ~listenparemeters.amp,
				\threshold, value,
				\falltime, ~listenparemeters.tempo.falltime,
				\checktime, ~listenparemeters.tempo.checkrate,
			])
		}.defer(0.2);// to make sure the SynthDef is ready to instantiate?
	}

	groupstart {
		groupst = SystemClock.seconds;
		parent.broadcastgroupstarted(); //
		hitflag = true;
		compass = compass + 1;
		if ( (~hutsunelookup > 0) && (compass > 2), {
			// TO DO: fix false positives
			hutsunetimeout = groupst + (60/~bpm) + ((60/~bpm) * ~hutsunelookup); // next expected hit should happen before hutsunetimeout
		});
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
		if (value == 0, { // there is signal
			if (hitflag.not, {
				this.groupstart();
			});
		}, { // there is silence
			if (hitflag, {
				this.groupend();
			}, {
				if ( hutsunetimeout.isNil.not, {
					this.checkhutsune();
				}, {
					this.checkreset();
				});
			});
		});
	}
}
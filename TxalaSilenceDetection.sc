// license GPL
// by www.ixi-audio.net

/*
t = TxalaSilenceDetection.new(this, s, true)
(not implemented yet) second argument is mode. 0 audioin, 1 MIDI in, 2 OSC in
third argument is answer mode. it sets the answer schedule time to groupdetect or groupend events
*/

TxalaSilenceDetection{

	var server, parent, tempocalc, <compass, hitflag, hutsunetimeout;
	var >processflag, resettime, <>answerposition;
	var synthOSCcb, <synth;

	*new {| aparent, aserver, ananswerposition = true |
		^super.new.initTxalaSilenceDetection(aparent, aserver, ananswerposition);
	}

	initTxalaSilenceDetection { arg aparent, aserver, ananswerposition;
		parent = aparent;
		server = aserver;
		answerposition = ananswerposition;
		tempocalc = TempoCalculator.new(2);
		this.reset()
	}

	reset {
		tempocalc.reset();
		compass = 0;
		hutsunetimeout = nil;
		processflag = false;
		hitflag = false;
		resettime = 3; // how many secs to wait before reseting the system

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

		SynthDef(\txalatempo, {| in=0, amp=1, threshold=0.5, falltime=0.1, checkrate=30 | //thres=0,1
			var detected;
			detected = DetectSilence.ar( SoundIn.ar(in)*amp, threshold, falltime );
			SendReply.kr(Impulse.kr(checkrate), '/txalasil', detected); // collect somewhere else
		}).add;


		{
			synth = Synth(\txalatempo, [
			\in, ~listenparemeters.in,
			\amp, ~listenparemeters.amp,
			\threshold, ~listenparemeters.tempo.threshold,
			\falltime, ~listenparemeters.tempo.falltime,
			\checktime, ~listenparemeters.tempo.checkrate,
			]);
		}.defer(1);

		OSCdef(\txalasilenceOSCdef, {|msg, time, addr, recvPort| this.process(msg[3])}, '/txalasil', server.addr);
	}

	updatethreshold { arg value;
		synth.free; // supercollider does not allow to update the amp parameter on the fly
		synth = nil;
		synth = Synth(\txalatempo, [
			\in, ~listenparemeters.in,
			\amp, ~listenparemeters.amp,
			\threshold, value,
			\falltime, ~listenparemeters.tempo.falltime,
			\checktime, ~listenparemeters.tempo.checkrate,
		])
	}

	lasthittime {
		^tempocalc.lasttime;
	}

	groupstart {
		hitflag = true;
		compass = compass + 1;
		~bpm = tempocalc.calculate();
		if ( (~hutsunelookup > 0), {
			hutsunetimeout = tempocalc.lasttime + (60/~bpm) + ((60/~bpm) * ~hutsunelookup); // next expected hit should go before that
		});
		if( (~answer && answerposition.not), { parent.answer() }); //schedule here the answer time acording to bpm
		("--------------------- start"+compass).postln;
	}

	// scheduling answers at this moment does not work with fast tempos as the tile of the signal steps
	// on the answer time. there is no silence between groups or that silence is too short.
	groupend {
		hitflag = false;
		parent.broadcastgroupended(); // needed by onset detector to close pattern groups
		if((~answer && answerposition), { parent.answer() }); // schedule here the answer time acording to bpm
		("--------------------- end"+compass).postln;
	}

	// checks for empty phases in the compass
	checkhutsune {
		if (Main.elapsedTime > hutsunetimeout, {
			"[[[[[[[ hutsune ]]]]]]]]".postln;
			parent.hutsune(); // need to update it was 0 hits
			tempocalc.pushlasttime(); // must update otherwise tempo drops
			hutsunetimeout = nil;
			if(~answer, { parent.answer() }); // broadcast
		});
	}

	// if too long after the last signal we received reset me
	checkreset {
		if ((Main.elapsedTime > (tempocalc.lasttime + resettime)), {
			"RESET SYSTEM".postln;
			this.reset();
			parent.reset();
		});
	}

	// loop for automatic txalaparta that listens to DetectSilence. triggered from an OSCFunc
	// calculates tempo and schedules answer in time with tempo. it tries to find out hutsunes
	// and resets the system if no input for longer than resettime secs
	process {arg value;
		var timetogo, gap;
		if ( processflag.not, {
			if (value == 0, { // signal
				if (hitflag.not, {
					this.groupstart();
				});
				"---------------------".postln;
			}, { // silence
				if (hitflag, { // we would need to forcestart here to avoid the silence gap detected after first hit.
					this.groupend();
				}, {
					if ( hutsunetimeout.isNil.not, {
						this.checkhutsune();
					}, {
						this.checkreset();
					});
					("." + ~bpm).postln;
				});
			});
		}, { // while I am answering
			("." + ~bpm).postln;
		});
		parent.loop(); // this is just to update some GUI
	}
}
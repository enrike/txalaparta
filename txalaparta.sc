/*
txalaparta. by ixi-audio.net
info@ixi-audio.net
license: GNU GPL

https://en.wikipedia.org/wiki/Txalaparta


this is a list that tries to summarise all possible controls for a digital txalaparta

* setup parameters
Tables
   number of tables
   type of wood of each table
   table’s shape (length, width, height)
   table’s pitch. harmonics or just partials? main freq?
   allow metal tubes? (Tobera)

Sticks
   type of wood of sticks
   length of sticks (useless?)
   shape of sticks (useless?)

Supports
   position of supports within the table
   amount of vibration allowed by supports (different materials used)

Music modes
   Old txalaparta (one table, only partials, txakun does not change)
   New txalaparta
   Newest txalaparta (tuned, one harmonic per table, emphasis on second beat, allows for 3 and 4 hits per beat,          ritmos ternarios y cuaternarios)

Limits
   Max amplitude
   Min/Max tempo (distance between beats)

* performance parameters
Time
    overall tempo: time between players + swing
    time between hits of a beat of each player + swing

Rhythm
    Number of hits by each player on a beat: 0, 1, 2 (txakun), 3 (txukutun), 4 (txakata)

Dynamics:
    general amplitude for both players
    emphasis on first or second hit? (forte/piano or piano/forte)
    amplitude offset of each beat from general amplitude
    amplitude offset of each hit from its beat amplitude

Timbre
    table being hit (per hit)
    location of each hit within the table’s length
    pitch (per hit)
*/

/*
Ideas para supercollider txalaparta :
- presets de settings de parámetros del interface
- añadir sistema de toque interactivo (persona + máquina)
      - incorporar escucha (en el caso de persona + máquina)
- incorporar memoria (propia y del otro)
- separar el loop del txakun y errena para que se mantenga el pulso independientemente del swing (git branch?). para esto necesito dos Tasks independientes que no se influyan mutuamente con su swing particular y que solo hagan caso al tempo
- standalone version supercollider
- send OSC out
- usar samples con filtros en vez de síntesis . diferentes materiales y tipos de madera. diferentes tamaños y diferentes notas.
- sistema de feedback (cuatro sliders verticales subiendo y bajando). para esto se puede defer() todos los golpes incluido el primero y disparar la animacion de los sliders con antelacion a que suceda el golpe.
*/

currentEnvironment;


/*
s.meter
s.makeGui
s.scope(2)
s.freqscope

s.prepareForRecord (path)
s.record(path)
s.stopRecording

s.volume = -20 //-90 <> 5.5
s.mute
s.unmute
*/




( // RUN ME HERE
//vars
var playF;

// GUI vars
var window, colorstates, beatbuttons, yfirstbuttons, classicBut, emphasysBut, output, sliders, nextautopilot, sndpath, samples, buffer, buffers;

//global vars
~tempo = 70; // tempo. txakun / errena
~swing = 0.1;
~gap = 0.22; // between hits. in txalaparta berria all gaps are more equal
~amp = 0.5;
~classictxakun = true; // in txalaparta zaharra the txakun always 2 hits
~pulse = false; // should we keep stedy pulse in the tempo or not?
~freqs = [230, 231]; // num of tables and pitches available
~emphasis = [true,false,false,false]; // which one is stronger. actualy just using first or last
~enabled = [true, true]; // switch on/off txakun and errena
~allowedbeats = [0, 1, 2, nil, nil]; // 0,1,2,3,4
~beatchance = [0.15, 0.25, 0.35, 0.15, 0.1];
~autopilot = false;
~autopilotrange = [5, 10]; // for instance
//~modes = [\txalaparta, \txalaparta2, \tobera, \plastic, \stone, \sine, \sample];
//~mode = ~modes[0];
// utility
~verbose = 1;


sndpath = thisProcess.nowExecutingPath.dirname ++ "/sounds/";
samples = (sndpath++"*").pathMatch;
("sndpath is" + sndpath).postln;
("samples are" + samples).postln;
buffers = [[nil, true], [nil, false], [nil, false], [nil, false]]; // [Buffer, enabled]
//~samples = List.newUsing( SoundFile.collect(thisProcess.nowExecutingPath.dirname ++ "/sounds/*") ); //




// THE SYNTHDEF

SynthDef(\playBuf, {arg outbus = 0, amp = 1, freq=1, bufnum = 0;
    Out.ar(outbus,
        amp * PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum) * freq, doneAction:2)!2
    )
}).add;


/* partials = [21.0, 258, 409, 581, 800, 1033, 1291, 1894, 2088]
[x/partials[1] for x in partials]
// en base al segundo parcial que es el mas fuerte
[0.08139534883720931, 1.0, 1.5852713178294573, 2.251937984496124, 3.10077519379845, 4.003875968992248, 5.003875968992248, 7.341085271317829, 8.093023255813954]
durs = [2.63, 3.00, 2.80, 2.60, 2.83, 3.00, 2.90, 1.38, 1.60]
*/
/*
SynthDef(\txalaparta, { arg freq=55, amp=1;
	var signal, parts, durs, amps, sust;

	parts = [0.08139534883720931, 1.0, 1.5852713178294573, 2.251937984496124, 3.10077519379845, 4.003875968992248, 5.003875968992248, 7.341085271317829, 8.093023255813954];
	durs = [0.263, 0.3, 0.28, 0.26, 0.283, 0.3, 0.29, 0.138, 0.16];
	amps = [0.1, 0.2, 0.05, 0.3, 0.3, 0.35, 0.08, 0.05, 0.05];
	sust = ExpRand(0.8, 1.2);

	signal =
	(WhiteNoise.ar(0.1)*EnvGen.ar(Env.perc(0.001, Rand(0.02, 0.035) ))) +
	(SinOsc.ar(freq*parts[0], 0, amps[0])*EnvGen.ar(Env.perc(0.001, durs[0]*sust)))+
	(SinOsc.ar(freq*parts[1], 0, amps[1])*EnvGen.ar(Env.perc(0.001, durs[1]*sust), doneAction:2))+
	(SinOsc.ar(freq*parts[2], 0, amps[2])*EnvGen.ar(Env.perc(0.001, durs[2]*sust)))+
	(SinOsc.ar(freq*parts[3], 0, amps[3])*EnvGen.ar(Env.perc(0.001, durs[3]*sust)))+
	(SinOsc.ar(freq*parts[4], 0, amps[4])*EnvGen.ar(Env.perc(0.001, durs[4]*sust)))+
	(SinOsc.ar(freq*parts[5], 0, amps[5])*EnvGen.ar(Env.perc(0.001, durs[5]*sust)))+
	(SinOsc.ar(freq*parts[6], 0, amps[6])*EnvGen.ar(Env.perc(0.001, durs[6]*sust)))+
	(SinOsc.ar(freq*parts[7], 0, amps[7])*EnvGen.ar(Env.perc(0.001, durs[7]*sust)))+
	(SinOsc.ar(freq*parts[8], 0, amps[8])*EnvGen.ar(Env.perc(0.001, durs[8]*sust)));
	Out.ar(0, Pan2.ar(signal*amp, 0));
}).add;


SynthDef(\txalaparta2, { arg freq=55, amp=1;
	var signal, parts, durs, amps, sust;

	parts = [0.26875, 1.0, 1.875, 2.825, 4.5625, 5.91875, 7.53125, 9.525, 10.975];
	durs = [0.263, 0.3, 0.28, 0.26, 0.283, 0.3, 0.29, 0.138, 0.16];
	amps = [0.1, 0.2, 0.05, 0.3, 0.3, 0.35, 0.08, 0.05, 0.05];
	sust = ExpRand(0.8, 1.2);

	signal =
	(WhiteNoise.ar(0.07)*EnvGen.ar(Env.perc(0.001, Rand(0.02, 0.04) ))) +
	(PinkNoise.ar(0.11)*EnvGen.ar(Env.perc(0.001, Rand(0.02, 0.06) ))) +
	(SinOsc.ar(freq*parts[0], 0, amps[0])*EnvGen.ar(Env.perc(0.001, durs[0]*sust)))+
	(SinOsc.ar(freq*parts[1], 0, amps[1])*EnvGen.ar(Env.perc(0.001, durs[1]*sust), doneAction:2))+
	(SinOsc.ar(freq*parts[2], 0, amps[2])*EnvGen.ar(Env.perc(0.001, durs[2]*sust)))+
	(SinOsc.ar(freq*parts[3], 0, amps[3])*EnvGen.ar(Env.perc(0.001, durs[3]*sust)))+
	(SinOsc.ar(freq*parts[4], 0, amps[4])*EnvGen.ar(Env.perc(0.001, durs[4]*sust)))+
	(SinOsc.ar(freq*parts[5], 0, amps[5])*EnvGen.ar(Env.perc(0.001, durs[5]*sust)))+
	(SinOsc.ar(freq*parts[6], 0, amps[6])*EnvGen.ar(Env.perc(0.001, durs[6]*sust)))+
	(SinOsc.ar(freq*parts[7], 0, amps[7])*EnvGen.ar(Env.perc(0.001, durs[7]*sust)))+
	(SinOsc.ar(freq*parts[8], 0, amps[8])*EnvGen.ar(Env.perc(0.001, durs[8]*sust)));
	Out.ar(0, Pan2.ar(signal*amp, 0));
}).add;


SynthDef(\tobera, { arg freq=55, amp=1;
	var signal, parts, durs, amps;

	parts = [0.08139534883720931, 1.0, 1.5852713178294573, 2.251937984496124, 3.10077519379845, 4.003875968992248, 5.003875968992248, 7.341085271317829, 8.093023255813954];
	durs = [2.63, 3, 2.8, 2.6, 2.83, 3, 2.9, 1.38, 1.6];
	amps = [0.1, 0.2, 0.05, 0.3, 0.3, 0.35, 0.08, 0.05, 0.05];

	signal =
	(BrownNoise.ar(0.2)*EnvGen.ar(Env.perc(0.001, 0.035 ))) +
	(SinOsc.ar(freq*parts[0], 0, amps[0])*EnvGen.ar(Env.perc(0.001, durs[0])))+
	(SinOsc.ar(freq*parts[1], 0, amps[1])*EnvGen.ar(Env.perc(0.001, durs[1]), doneAction:2))+
	(SinOsc.ar(freq*parts[2], 0, amps[2])*EnvGen.ar(Env.perc(0.001, durs[2])))+
	(SinOsc.ar(freq*parts[3], 0, amps[3])*EnvGen.ar(Env.perc(0.001, durs[3])))+
	(SinOsc.ar(freq*parts[4], 0, amps[4])*EnvGen.ar(Env.perc(0.001, durs[4])))+
	(SinOsc.ar(freq*parts[5], 0, amps[5])*EnvGen.ar(Env.perc(0.001, durs[5])))+
	(SinOsc.ar(freq*parts[6], 0, amps[6])*EnvGen.ar(Env.perc(0.001, durs[6])))+
	(SinOsc.ar(freq*parts[7], 0, amps[7])*EnvGen.ar(Env.perc(0.001, durs[7])))+
	(SinOsc.ar(freq*parts[8], 0, amps[8])*EnvGen.ar(Env.perc(0.001, durs[8])));
	Out.ar(0, Pan2.ar(signal*amp, 0));
}).add;

*/


/*
Pbind(\instrument, \tobera, \freq, 210, \sustain, 0.1, \amp, 0.6).play
*/


// TXALAPARTA ////////////////////
playF = Routine({
	var txakun; // classical txakun only is limited to two beats
	var intermakilaswing, localstep, localtempo, localamp;

	txakun = true; // play starts with txakun
	nextautopilot = 0;

	inf.do({ arg stepcounter; // txakun > errena cycle
		var numbeats; // numbeats needs to be here to be nill each time

		localtempo = (60/~tempo) + ~swing.rand - (~swing/2); //offset of beat within the ideal position that should have

		if (~pulse, // does tempo keep with pulse?
			{(60/~tempo).wait},
			{localtempo.wait}
		);

		if ( (~autopilot && (stepcounter >= nextautopilot)) , {
			var sl;
			sl = sliders.choose;
			sl.valueAction = rrand(sl.controlSpec.minval, sl.controlSpec.maxval);
			nextautopilot = stepcounter + rrand(~autopilotrange[0], ~autopilotrange[1]); // next buelta to change
			if (~verbose>0, {("autopilot! next autopilot scheduled for step" + nextautopilot).postln});
		});

		if ( (txakun && ~enabled[0]) || (txakun.not && ~enabled[1]), {

			if (~classictxakun && txakun, // how many hits this time?
				{ numbeats = 2 },
				{ { numbeats == nil }.while( {numbeats = ~allowedbeats.wchoose(~beatchance)} ) } // avoid nil
			);

			localstep = (~gap/numbeats); // * localtempo  // perfect step between makila hits before swing
			intermakilaswing = rrand(~swing/numbeats.neg, ~swing/numbeats);

			if (~amp > 0, {localamp = ~amp + 0.3.rand-0.15}, {localamp = 0}); //local amp swing

			numbeats.do({ arg index; // for each makila one hit
				var hitfreq, hitstep, hitamp, plank; //here to be reseted each time
				if (~amp > 0, { // emphasis on first or on last hit?
					if ((numbeats == (index+1) && ~emphasis[1]) || (index == 0 && ~emphasis[0]),
						{hitamp = localamp + 0.30},
						{hitamp = localamp}
					);
				});

				plank = [nil, false]; //buffers.choose[0];
				{ plank[1] == false }.while( { plank = buffers.choose }); // avoid nil
				plank.postln;
				hitfreq = (~freqs.choose) + 0.6.rand; // freq swing
				hitstep = localstep + rrand(intermakilaswing.neg, intermakilaswing);

				//{ Synth(~mode, [\amp, hitamp, \freq, hitfreq]) }.defer( hitstep * index);
				{ Synth(\playBuf, [\amp, hitamp, \freq, 1+rrand(-0.005, 0.005), \bufnum, plank[0].bufnum]) }.defer( localtempo + (hitstep * index));

				if (~verbose>2, {[hitamp, hitfreq, hitstep].postln});
			});
			output.string = {if (txakun, {"txakun"},{"errena"})}.value + numbeats.asString;
			if (~verbose>0, {[stepcounter, txakun, numbeats].postln});
			if (~verbose>0 && txakun.not, {"-- buelta --".postln}); // every other is a buelta
		});

		txakun = txakun.not;
	}) // end inf loop
});


// GUI /////////////////////////////////////////
window = Window("Txalaparta. www.ixi-audio.net");//, Rect(100, 100, 350, 400));
window.alwaysOnTop = true;
window.onClose = {AppClock.clear};
window.front;
beatbuttons = [nil, nil, nil, nil, nil];
sliders = [nil, nil, nil];

s.boot; //////// BOOT SERVER //////////////////

// TIME CONTROLS
sliders[0] = EZSlider( window,         // parent
              Rect(-10,5,350,20),    // bounds
              "tempo",  // label
              ControlSpec(30, 250, \lin, 1, ~tempo, "BPMs"),     // controlSpec
		      { arg ez;
		        ~tempo = ez.value;
		      },
              initVal: ~tempo
	       );

sliders[1] = EZSlider( window,         // parent
              Rect(-10,35,350,20),    // bounds
              "swing",  // label
              ControlSpec(0.001, 1, \lin, 0.001, ~swing, "ms"),     // controlSpec
		      { arg ez;
                ~swing = ez.value;
		      },
              initVal: ~swing
	       );

sliders[2] = EZSlider( window,         // parent
              Rect(-10,65,350,20),    // bounds
              "gap",  // label
              ControlSpec(0.001, 1, \lin, 0.001, ~gap, "ms"),     // controlSpec
		      { arg ez;
                ~gap = ez.value;
		      },
              initVal: ~gap
	       );

// but dont store the amplitude!
EZSlider( window,         // parent
              Rect(-10,95,350,20),    // bounds
              "amp",  // label
              ControlSpec(0, 1, \lin, 0.01, ~amp, "ms"), //\amp,     // controlSpec
		      { arg ez;
                ~amp = ez.value;
		      },
              initVal: ~amp
	       );




yfirstbuttons = 125;


// TXAKUN
	Button(window, Rect(10,yfirstbuttons,100,25))
        .states_([
            ["txakun", Color.white, Color.black],
            ["txakun", Color.black, Color.red],
        ])
        .action_({ arg butt;
	       ~enabled[0] = butt.value.asBoolean;
        })
        .valueAction_(1);

// ERRENA
	Button(window, Rect(110,yfirstbuttons,100,25))
        .states_([
            ["errena", Color.white, Color.black],
            ["errena", Color.black, Color.red],
        ])
        .action_({ arg butt;
	      	~enabled[1] = butt.value.asBoolean;
        })
        .valueAction_(1);

// AUTOPILOT
	Button(window, Rect(210,yfirstbuttons,100,25))
        .states_([
            ["Autopilot", Color.white, Color.black],
            ["Autopilot", Color.black, Color.red],
        ])
        .action_({ arg butt;
	       ~autopilot = butt.value.asBoolean;
	       nextautopilot = 0; // force go next round
          if (~verbose>0 && ~autopilot, {("next autopilot scheduled for step" + nextautopilot).postln});
        });
        //.valueAction_(0);

// BEATS


beatbuttons[0] = Button(window, Rect(10,yfirstbuttons+25,20,25))
        .states_([
            ["0", Color.white, Color.black],
            ["0", Color.black, Color.red],
        ])
        .action_({ arg butt;
	        if (butt.value.asBoolean, {~allowedbeats[0]=0},{~allowedbeats[0]=nil});
        });
beatbuttons[0].valueAction = 1;

beatbuttons[1] = Button(window, Rect(30,yfirstbuttons+25,20,25))
        .states_([
            ["1", Color.white, Color.black],
            ["1", Color.black, Color.red],
        ])
        .action_({ arg butt;
	        if (butt.value.asBoolean, {~allowedbeats[1]=1},{~allowedbeats[1]=nil});
        });
beatbuttons[1].valueAction = 1;

beatbuttons[2] = Button(window, Rect(50,yfirstbuttons+25,20,25))
        .states_([
            ["2", Color.black, Color.red],
        ])
        .action_({ arg butt;}); // NO ACTION. THIS IS ALWAYS ON
beatbuttons[2].valueAction = 1;

beatbuttons[3] = Button(window, Rect(70,yfirstbuttons+25,20,25))
        .states_([
            ["3", Color.white, Color.black],
            ["3", Color.black, Color.red],
        ])
        .action_({ arg butt;
	        if (butt.value.asBoolean, {~allowedbeats[3]=3},{~allowedbeats[3]=nil});
        });
beatbuttons[3].valueAction = 0;

beatbuttons[4] =  Button(window, Rect(90,yfirstbuttons+25,20,25))
        .states_([
            ["4", Color.white, Color.black],
            ["4", Color.black, Color.red],
        ])
        .action_({ arg butt;
	        if (butt.value.asBoolean, {~allowedbeats[4]=4},{~allowedbeats[4]=nil});
        });
beatbuttons[4].valueAction = 0;


// EMPHASIS
emphasysBut = Button(window, Rect(110,yfirstbuttons+25,100,25))
        .states_([
            ["last emphasis", Color.white, Color.black],
            ["last emphasis", Color.black, Color.red],
        ])
        .action_({ arg butt;
	        ~emphasis = [butt.value.asBoolean.not, butt.value.asBoolean];
        })
        .valueAction_(1);

// PULSE
Button(window, Rect(210,yfirstbuttons+25,100,25))
        .states_([
            ["maintain pulse", Color.white, Color.black],
            ["maintain pulse", Color.black, Color.red],
        ])
        .action_({ arg butt;
	         ~pulse = butt.value.asBoolean;
        });

// CLASSIC TXAKUN
classicBut = Button(window, Rect(110,yfirstbuttons+50,100,25))
        .states_([
            ["classic txakun", Color.white, Color.black],
            ["classic txakun", Color.black, Color.red],
        ])
        .action_({ arg butt;
	         ~classictxakun = butt.value.asBoolean;
        });
classicBut.valueAction = 0;


// MODE
	Button(window, Rect(10,yfirstbuttons+50,100,25))
        .states_([
            ["zaharra", Color.white, Color.black],
            ["zaharra", Color.black, Color.red],
        ])
        .action_({ arg butt;
	      	~classictxakun = butt.value.asBoolean.not;
	        beatbuttons.do({arg but, ind;
		       if ( but != nil, {
			    if ( butt.value.asBoolean,
				   {
					if ( ind < 2, {but.valueAction = 1}, {but.valueAction = 0});
					classicBut.valueAction = 1;
					emphasysBut.valueAction = 1
				   },
				   {
					but.valueAction = 1;
					classicBut.valueAction = 0;
					emphasysBut.valueAction = 0
				  });
               });
	        });
        })
        .valueAction_(1);


/*PopUpMenu(window,Rect(210,yfirstbuttons+25,100,20))
    .items_(~modes)
    .action_({ arg pop;
	    ~mode = ~modes[pop.value];
     });*/


// PLAY
	Button(window, Rect(10,yfirstbuttons+75,200,25))
        .states_([
            ["play/stop", Color.white, Color.black],
            ["play/stop", Color.black, Color.red],
        ])
        .action_({ arg butt;
	       if ( butt.value.asBoolean, { AppClock.play(playF)}, {AppClock.clear});
        });
        //.valueAction_(0);

// SERVER
	Button(window, Rect(10,yfirstbuttons+100,100,25))
        .states_([
            ["server window", Color.white, Color.grey],
        ])
        .action_({ arg butt;
	       s.makeGui;
        });
        //.valueAction_(0);

// VERBOSE
	Button(window, Rect(110,yfirstbuttons+100,20,25))
        .states_([
            ["V", Color.white, Color.grey],
	        ["V", Color.white, Color.blue],
	        ["V", Color.white, Color.green],
	        ["V", Color.white, Color.red]
        ])
        .action_({ arg butt;
	       ~verbose = butt.value;
        })
        .valueAction_(~verbose);


// PLANKS - OHOLAK //////////////////////////////////
//1
	Button(window, Rect(10,yfirstbuttons+150,20,20))
        .states_([
            ["1", Color.black, Color.red],
        ])
        .action_({ arg butt; }); // NO ACTION. THIS IS ALWAYS ON

	PopUpMenu(window,Rect(35,yfirstbuttons+150,250,20))
	.items_(samples.asArray.collect({arg item; PathName.new(item).fileName}))
	     .action_({ arg menu;
		     //buffer = Buffer.read(s, sndpath ++ menu.item);
	         buffers[0][0] = Buffer.read(s, sndpath ++ menu.item);
	         ("loading" + menu.item + "with bufnum" + buffers[0][0].bufnum).postln;
	     })
	.valueAction_(0);
//2
	Button(window, Rect(10,yfirstbuttons+170,20,20))
        .states_([
            ["2", Color.white, Color.black],
            ["2", Color.black, Color.red],
        ])
        .action_({ arg butt;
	       buffers[1][1] = butt.value.asBoolean;
        });
	PopUpMenu(window,Rect(35,yfirstbuttons+170,250,20))
	.items_(samples.asArray.collect({arg item; PathName.new(item).fileName}))
	     .action_({ arg menu;
		     //buffer = Buffer.read(s, sndpath ++ menu.item);
	           buffers[1][0] = Buffer.read(s, sndpath ++ menu.item);
	           ("loading" + menu.item + "with bufnum" + buffers[1][0].bufnum).postln;
	     })
	.valueAction_(1);
//3
	Button(window, Rect(10,yfirstbuttons+190,20,20))
        .states_([
            ["3", Color.white, Color.black],
            ["3", Color.black, Color.red],
        ])
        .action_({ arg butt;
             buffers[2][1] = butt.value.asBoolean;
        });
	PopUpMenu(window,Rect(35,yfirstbuttons+190,250,20))
	.items_(samples.asArray.collect({arg item; PathName.new(item).fileName}))
	     .action_({ arg menu;
		     //buffer = Buffer.read(s, sndpath ++ menu.item);
	           buffers[2][0] = Buffer.read(s, sndpath ++ menu.item);
	           ("loading" + menu.item + "with bufnum" + buffers[2][0].bufnum).postln;
	     })
	.valueAction_(2);
//4
	Button(window, Rect(10,yfirstbuttons+210,20,20))
        .states_([
            ["4", Color.white, Color.black],
            ["4", Color.black, Color.red],
        ])
        .action_({ arg butt;
             buffers[3][1] = butt.value.asBoolean;
        });
	PopUpMenu(window,Rect(35,yfirstbuttons+210,250,20))
	.items_(samples.asArray.collect({arg item; PathName.new(item).fileName}))
	     .action_({ arg menu;
		     //buffer = Buffer.read(s, sndpath ++ menu.item);
	           buffers[3][0] = Buffer.read(s, sndpath ++ menu.item);
	           ("loading" + menu.item + "with bufnum" + buffers[3][0].bufnum).postln;
	     })
	.valueAction_(3);


output = StaticText(window, Rect(10, yfirstbuttons+125, 200, 20));

if (~verbose>0, {currentEnvironment.postln});
if (~verbose>0, {buffers});
)


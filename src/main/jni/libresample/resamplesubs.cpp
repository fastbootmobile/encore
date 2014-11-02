/* resamplesubs.cpp - sampling rate conversion subroutines */
// Altered version
#include "resample.h"

#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <string.h>

#define IBUFFSIZE 4096                         /* Input buffer size */

#ifdef DEBUG
static int pof = 0;             /* positive overflow count */
static int nof = 0;             /* negative overflow count */
#endif

static INLINE HWORD WordToHword(WORD v, int scl)
{
    HWORD out;
    WORD llsb = (1<<(scl-1));
    v += llsb;          /* round */
    v >>= scl;
    if (v>MAX_HWORD) {
#ifdef DEBUG
        if (pof == 0)
          fprintf(stderr, "*** resample: sound sample overflow\n");
        else if ((pof % 10000) == 0)
          fprintf(stderr, "*** resample: another ten thousand overflows\n");
        pof++;
#endif
        v = MAX_HWORD;
    } else if (v < MIN_HWORD) {
#ifdef DEBUG
        if (nof == 0)
          fprintf(stderr, "*** resample: sound sample (-) overflow\n");
        else if ((nof % 1000) == 0)
          fprintf(stderr, "*** resample: another thousand (-) overflows\n");
        nof++;
#endif
        v = MIN_HWORD;
    }   
    out = (HWORD) v;
    return out;
}

/* Sampling rate conversion using linear interpolation for maximum speed.
 */
static int 
  SrcLinear(HWORD X[], HWORD Y[], double factor, UWORD *Time, UHWORD Nx)
{
    HWORD iconst;
    HWORD *Xp, *Ystart;
    WORD v,x1,x2;
    
    double dt;                  /* Step through input signal */ 
    UWORD dtb;                  /* Fixed-point version of Dt */
    UWORD endTime;              /* When Time reaches EndTime, return to user */
    
    dt = 1.0/factor;            /* Output sampling period */
    dtb = dt*(1<<Np) + 0.5;     /* Fixed-point representation */
    
    Ystart = Y;
    endTime = *Time + (1<<Np)*(WORD)Nx;
    while (*Time < endTime)
    {
        iconst = (*Time) & Pmask;
        Xp = &X[(*Time)>>Np];      /* Ptr to current input sample */
        x1 = *Xp++;
        x2 = *Xp;
        x1 *= ((1<<Np)-iconst);
        x2 *= iconst;
        v = x1 + x2;
        *Y++ = WordToHword(v,Np);   /* Deposit output */
        *Time += dtb;               /* Move to next sample by time increment */
    }
    return (Y - Ystart);            /* Return number of output samples */
}

/**
 * ADDED/ADAPTED FOR OMNIMUSIC, from resampleFast in original distribution
 **/
int resampleBuffersFast(  /* number of output samples returned */
    double factor,              /* factor = Sndout/Sndin */
    HWORD* X1,                   /* input and output buffers */
    HWORD* Y1,
    int inCount)                /* number of input samples to convert */
{
    UWORD Time, Time2;          /* Current time/pos in input sample */
    UHWORD Xp, Ncreep, Xoff, Xread;
    // int OBUFFSIZE = (int)(((double)IBUFFSIZE)*factor+2.0);
    UHWORD Nout, Nx;
    int i, Ycount, last;

    Xoff = 10;

    Nx = inCount - 2*Xoff;      /* # of samples to process each iteration */
    last = 0;                   /* Have not read last input sample yet */
    Ycount = 0;                 /* Current sample and length of output file */

    Xp = Xoff;                  /* Current "now"-sample pointer for input */
    Xread = Xoff;               /* Position in input array to read into */
    Time = (Xoff<<Np);          /* Current-time pointer for converter */

    /* Resample stuff in input buffer */
    Time2 = Time;
    Nout=SrcLinear(X1,Y1,factor,&Time,Nx);

    Time -= (Nx<<Np);       /* Move converter Nx samples back in time */
    Xp += Nx;               /* Advance by number of samples processed */
    Ncreep = (Time>>Np) - Xoff; /* Calc time accumulation in Time */
    if (Ncreep) {
        Time -= (Ncreep<<Np);    /* Remove time accumulation */
        Xp += Ncreep;            /* and add it to read pointer */
    }
    for (i=0; i<inCount-Xp+Xoff; i++) { /* Copy part of input signal */
        X1[i] = X1[i+Xp-Xoff]; /* that must be re-used */
    }
    if (last) {             /* If near end of sample... */
        last -= Xp;         /* ...keep track were it ends */
        if (!last)          /* Lengthen input by 1 sample if... */
          last++;           /* ...needed to keep flag TRUE */
    }
    Xread = i;              /* Pos in input buff to read new data into */
    Xp = Xoff;

    Ycount += Nout;

    return(Ycount);             /* Return # of samples in output file */
}
/**
 * END ADD
 **/


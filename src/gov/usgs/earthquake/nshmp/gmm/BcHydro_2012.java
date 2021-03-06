package gov.usgs.earthquake.nshmp.gmm;

import static gov.usgs.earthquake.nshmp.gmm.CampbellBozorgnia_2014.basinResponseTerm;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.MW;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.RRUP;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.VS30;
import static gov.usgs.earthquake.nshmp.gmm.GmmInput.Field.ZTOP;
import static gov.usgs.earthquake.nshmp.gmm.Imt.PGA;
import static java.lang.Math.exp;
import static java.lang.Math.log;
import static java.lang.Math.min;
import static java.lang.Math.pow;

import com.google.common.collect.Range;

import java.util.Map;

import gov.usgs.earthquake.nshmp.eq.Earthquakes;
import gov.usgs.earthquake.nshmp.gmm.GmmInput.Constraints;

/**
 * Abstract implementation of the subduction ground motion model created for BC
 * Hydro, Canada, by Addo, Abrahamson, & Youngs (2012). This implementation
 * matches that used in the USGS NSHM as supplied by N. Abrahamson.
 *
 * <p>This model supports both slab and interface type events. In the 2008
 * NSHMP, the 'interface' form is used with the Cascadia subduction zone models
 * and the 'slab' form is used with gridded 'deep' events in northern California
 * and the Pacific Northwest.
 *
 * <p><b>Note:</b> Direct instantiation of {@code GroundMotionModel}s is
 * prohibited. Use {@link Gmm#instance(Imt)} to retrieve an instance for a
 * desired {@link Imt}.
 *
 * <p><b>Implementation notes:</b> <ol><li>Treats all sites as
 * forearc.</li><li>'zTop' is interpreted as hypocentral depth and is only used
 * for slab events; it is limited to 125 km, consistent with other subduction
 * models.</li><li>The DeltaC1 term is keyed to the 'middle' BC Hydro branch for
 * interface events and fixed at -0.3 for slab events.</li></ol>
 *
 * <p><b>Reference:</b> Addo, K., Abrahamson, N., and Youngs, R., (BC Hydro),
 * 2012, Probabilistic seismic hazard analysis (PSHA) model—Ground motion
 * characterization (GMC) model: Report E658, v. 3, November.
 *
 * <p><b>Component:</b> Geometric mean of two horizontal components
 *
 * @author Peter Powers
 * @see Gmm#BCHYDRO_12_INTER
 * @see Gmm#BCHYDRO_12_SLAB
 * @see Gmm#BCHYDRO_12_BASIN_INTERFACE
 * @see Gmm#BCHYDRO_12_BASIN_SLAB
 */
public abstract class BcHydro_2012 implements GroundMotionModel {

  static final String NAME = "BC Hydro (2012)";

  // TODO will probably want to have constraints per-implementation (e.g. slab
  // vs interface depth limits)
  static final Constraints CONSTRAINTS = Constraints.builder()
      .set(MW, Range.closed(5.0, 9.5))
      .set(RRUP, Range.closed(0.0, 1000.0))
      .set(ZTOP, Earthquakes.SLAB_DEPTH_RANGE)
      .set(VS30, Range.closed(150.0, 1000.0))
      .build();

  static final CoefficientContainer COEFFS = new CoefficientContainer("BCHydro12.csv");

  private static final double C1 = 7.8;
  private static final double T3 = 0.1;
  private static final double T4 = 0.9;
  private static final double T5 = 0.0;
  private static final double T9 = 0.4;
  private static final double C4 = 10.0;
  private static final double C = 1.88;
  private static final double N = 1.18;
  private static final double VSS_MAX = 1000.0;
  private static final double SIGMA = 0.74;
  private static final double ΔC1_SLAB = -0.3;

  private static final class Coefficients {

    final double vlin, b, θ1, θ2, θ6, θ10, θ11, θ12, θ13, θ14, ΔC1mid;

    // not currently used
    // final double t7, t8, t15, t16, dC1lo, dC1hi;

    Coefficients(Imt imt, CoefficientContainer cc) {
      Map<String, Double> coeffs = cc.get(imt);
      vlin = coeffs.get("vlin");
      b = coeffs.get("b");
      θ1 = coeffs.get("t1");
      θ2 = coeffs.get("t2");
      θ6 = coeffs.get("t6");
      θ10 = coeffs.get("t10");
      θ11 = coeffs.get("t11");
      θ12 = coeffs.get("t12");
      θ13 = coeffs.get("t13");
      θ14 = coeffs.get("t14");
      ΔC1mid = coeffs.get("dC1mid");
    }
  }

  private final Coefficients coeffs;
  private final Coefficients coeffsPGA;
  private final CampbellBozorgnia_2014 cb14;

  BcHydro_2012(final Imt imt) {
    coeffs = new Coefficients(imt, COEFFS);
    coeffsPGA = new Coefficients(PGA, COEFFS);
    cb14 = new CampbellBozorgnia_2014(imt);
  }

  @Override
  public final ScalarGroundMotion calc(final GmmInput in) {

    // possibly picking up basin term from CB14
    double fBasin = basinTerm() ? basinResponseTerm(cb14.coeffs, in.vs30, in.z2p5) : 0.0;

    // pgaRock only required to compute non-linear site response
    // when vs30 is less than period-dependent vlin cutoff
    double pgaRock = (in.vs30 < coeffs.vlin)
        ? exp(calcMean(coeffsPGA, isSlab(), fBasin, 0.0, in.Mw, in.rRup, in.zTop, 1000.0))
        : 0.0;
    double μ = calcMean(coeffs, isSlab(), fBasin, pgaRock, in.Mw, in.rRup, in.zTop, in.vs30);
    return DefaultScalarGroundMotion.create(μ, SIGMA);
  }

  abstract boolean isSlab();

  abstract boolean basinTerm();

  private static final double calcMean(final Coefficients c, final boolean slab,
      final double fBasin, final double pgaRock, final double Mw, final double rRup,
      final double zTop, final double vs30) {

    double ΔC1 = (slab ? ΔC1_SLAB : c.ΔC1mid);
    double mCut = C1 + ΔC1;
    double t13m = c.θ13 * (10 - Mw) * (10 - Mw);
    double fMag = (Mw <= mCut ? T4 : T5) * (Mw - mCut) + t13m;

    // no depth term for interface events
    double fDepth = slab ? c.θ11 * (min(zTop, 125.0) - 60.) : 0.0;

    double vsS = min(vs30, VSS_MAX);

    double fSite = c.θ12 * log(vsS / c.vlin);
    if (vs30 < c.vlin) { // whether or not we use pgaRock
      fSite += -c.b * log(pgaRock + C) + c.b * log(pgaRock + C * pow((vsS / c.vlin), N));
    } else {
      // for pgaRock loop, vs=1000 > vlinPGA=865
      fSite += c.b * N * log(vsS / c.vlin);
    }

    return c.θ1 + T4 * ΔC1 +
        (c.θ2 + (slab ? c.θ14 : 0.0) + T3 * (Mw - 7.8)) *
            log(rRup + C4 * exp((Mw - 6.0) * T9)) +
        c.θ6 * rRup + (slab ? c.θ10 : 0.0) + fMag +
        fDepth +
        // fterm + no fterm for forearc sites
        fSite +
        fBasin;
  }

  static final class Interface extends BcHydro_2012 {
    static final String NAME = BcHydro_2012.NAME + ": Interface";

    Interface(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isSlab() {
      return false;
    }

    @Override
    boolean basinTerm() {
      return false;
    }
  }

  static final class Slab extends BcHydro_2012 {
    static final String NAME = BcHydro_2012.NAME + ": Slab";

    Slab(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isSlab() {
      return true;
    }

    @Override
    boolean basinTerm() {
      return false;
    }
  }

  static final class BasinInterface extends BcHydro_2012 {
    static final String NAME = BcHydro_2012.NAME + " Basin: Interface";

    BasinInterface(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isSlab() {
      return false;
    }

    @Override
    boolean basinTerm() {
      return true;
    }
  }

  static final class BasinSlab extends BcHydro_2012 {
    static final String NAME = BcHydro_2012.NAME + " Basin: Slab";

    BasinSlab(Imt imt) {
      super(imt);
    }

    @Override
    final boolean isSlab() {
      return true;
    }

    @Override
    boolean basinTerm() {
      return true;
    }
  }
}

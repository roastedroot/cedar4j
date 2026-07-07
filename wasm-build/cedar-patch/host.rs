//! Host extension: a bridge function `ext()` that dispatches to a
//! runtime-registered callback and returns the result as a Cedar value.

use crate::ast::{
    CallStyle, Extension, ExtensionFunction, ExtensionOutputValue,
    Literal, Name, Value, ValueKind,
};
use crate::entities::json::err::JsonDeserializationErrorContext;
use crate::entities::CedarValueJson;
use crate::evaluator;
use crate::evaluator::RestrictedEvaluator;
use crate::extensions::Extensions;

use std::sync::{LazyLock, RwLock};

mod names {
    use crate::ast::Name;
    use std::sync::LazyLock;

    pub static EXTENSION_NAME: LazyLock<Name> = LazyLock::new(|| {
        Name::parse_unqualified_name("host").expect("valid identifier")
    });
    pub static EXT_NAME: LazyLock<Name> = LazyLock::new(|| {
        Name::parse_unqualified_name("ext").expect("valid identifier")
    });
}

/// Callback type: takes (function_name, args_json) → result_json.
pub type HostCallbackFn = Box<dyn Fn(&str, &str) -> Result<String, String> + Send + Sync>;

static HOST_CALLBACK: LazyLock<RwLock<Option<HostCallbackFn>>> =
    LazyLock::new(|| RwLock::new(None));

/// Register the host callback that `ext()` will dispatch to.
pub fn set_host_callback(
    cb: impl Fn(&str, &str) -> Result<String, String> + Send + Sync + 'static,
) {
    *HOST_CALLBACK.write().unwrap() = Some(Box::new(cb));
}

fn make_err(msg: String) -> evaluator::EvaluationError {
    evaluator::EvaluationError::failed_extension_function_application(
        names::EXT_NAME.clone(),
        msg,
        None,
        None,
    )
}

fn value_to_json(v: &Value) -> serde_json::Value {
    match &v.value {
        ValueKind::Lit(Literal::Bool(b)) => serde_json::Value::Bool(*b),
        ValueKind::Lit(Literal::Long(n)) => serde_json::json!(n),
        ValueKind::Lit(Literal::String(s)) => serde_json::Value::String(s.to_string()),
        _ => serde_json::Value::String(format!("{v}")),
    }
}

fn json_to_cedar_value(json_str: &str) -> evaluator::Result<Value> {
    let json: serde_json::Value =
        serde_json::from_str(json_str).map_err(|e| make_err(format!("invalid JSON: {e}")))?;
    let cedar_json: CedarValueJson =
        serde_json::from_value(json).map_err(|e| make_err(format!("invalid Cedar value: {e}")))?;
    let expr = cedar_json
        .into_expr(&|| JsonDeserializationErrorContext::Context)
        .map_err(|e| make_err(format!("JSON conversion error: {e}")))?;
    let evaluator = RestrictedEvaluator::new(Extensions::all_available());
    evaluator
        .interpret(expr.as_borrowed())
        .map_err(|e| make_err(format!("evaluation error: {e}")))
}

/// Construct the host extension.
pub fn extension() -> Extension {
    let ext_fn = ExtensionFunction::binary(
        names::EXT_NAME.clone(),
        CallStyle::FunctionStyle,
        Box::new(|name_val: &Value, arg_val: &Value| {
            let name = match &name_val.value {
                ValueKind::Lit(Literal::String(s)) => s.to_string(),
                _ => return Err(make_err(format!(
                    "ext() first argument must be a string, got: {name_val}"
                ))),
            };

            let args_json = serde_json::to_string(&serde_json::Value::Array(vec![
                value_to_json(arg_val),
            ]))
            .unwrap();

            let lock = HOST_CALLBACK.read().unwrap();
            let cb = lock
                .as_ref()
                .ok_or_else(|| make_err("no host callback registered".into()))?;

            let result_json = cb(&name, &args_json).map_err(make_err)?;
            let value = json_to_cedar_value(&result_json)?;
            Ok(ExtensionOutputValue::Known(value))
        }),
        crate::entities::SchemaType::Record {
            attrs: std::collections::BTreeMap::new(),
            open_attrs: true,
        },
        (crate::entities::SchemaType::String, crate::entities::SchemaType::String),
    );

    Extension::new(
        names::EXTENSION_NAME.clone(),
        vec![ext_fn],
        std::iter::empty::<Name>(),
    )
}
